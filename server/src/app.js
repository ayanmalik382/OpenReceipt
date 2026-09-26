require('dotenv').config();
'use strict';
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const { sendOtp } = require('./mailer');

const ENTITIES = new Set(['business', 'supplier', 'product', 'customer', 'order', 'orderItem', 'payment', 'expense']);
const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;
const OTP_TTL_MS = 10 * 60 * 1000;
const OTP_COOLDOWN_MS = 60 * 1000;
const MAX_OTP_ATTEMPTS = 5;
const PULL_LIMIT = 1000;
const MAX_CHANGES = 500;
const MAX_DATA_BYTES = 100 * 1024;
const DUMMY_HASH = bcrypt.hashSync('dummy-password-for-constant-time', 10);

function migrate(db) {
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT UNIQUE NOT NULL,
      pass_hash TEXT NOT NULL,
      verified INTEGER NOT NULL DEFAULT 0,
      otp_hash TEXT, otp_expires INTEGER, otp_attempts INTEGER NOT NULL DEFAULT 0, otp_sent_at INTEGER,
      seq INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS records (
      user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      entity TEXT NOT NULL,
      id TEXT NOT NULL,
      updated_at INTEGER NOT NULL,
      deleted INTEGER NOT NULL,
      data TEXT NOT NULL,
      seq INTEGER NOT NULL,
      PRIMARY KEY (user_id, entity, id)
    );
    CREATE INDEX IF NOT EXISTS idx_records_seq ON records(user_id, seq);
  `);
}

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const bad = (res, status, error, extra = {}) => res.status(status).json({ error, ...extra });

function createApp(db, secret) {
  if (!secret || secret.length < 32) throw new Error('JWT_SECRET must be at least 32 characters');
  migrate(db);
  const app = express();
  app.disable('x-powered-by');
  if (process.env.TRUST_PROXY) app.set('trust proxy', Number(process.env.TRUST_PROXY));
  app.use(helmet());
  app.use(express.json({ limit: '2mb' }));

  const limiterOpts = { standardHeaders: true, legacyHeaders: false, message: { error: 'Too many requests. Please wait a minute.', code: 'RATE_LIMITED' } };
  const authLimiter = rateLimit({ windowMs: 60_000, limit: Number(process.env.AUTH_RATE || 20), ...limiterOpts });
  const syncLimiter = rateLimit({ windowMs: 60_000, limit: 120, ...limiterOpts });

  const hashOtp = (email, otp) => crypto.createHmac('sha256', secret).update(`${email}:${otp}`).digest('hex');
  const signToken = (user) => jwt.sign({ uid: user.id }, secret, { algorithm: 'HS256', expiresIn: '30d' });
  const userByEmail = (email) => db.prepare('SELECT * FROM users WHERE email = ?').get(email);
  const normEmail = (e) => (typeof e === 'string' ? e.trim().toLowerCase() : '');
  const validPassword = (p) => typeof p === 'string' && p.length >= 8 && p.length <= 72;

  async function issueOtp(user) {
    const now = Date.now();
    if (user.otp_sent_at && now - user.otp_sent_at < OTP_COOLDOWN_MS) return false;
    const otp = String(crypto.randomInt(100000, 1000000));
    db.prepare('UPDATE users SET otp_hash=?, otp_expires=?, otp_attempts=0, otp_sent_at=? WHERE id=?')
      .run(hashOtp(user.email, otp), now + OTP_TTL_MS, now, user.id);
    await sendOtp(user.email, otp);
    return true;
  }

  function checkOtp(user, otp) {
    if (!user || !user.otp_hash || !user.otp_expires || Date.now() > user.otp_expires) return false;
    if (user.otp_attempts >= MAX_OTP_ATTEMPTS) return false;
    db.prepare('UPDATE users SET otp_attempts = otp_attempts + 1 WHERE id=?').run(user.id);
    if (typeof otp !== 'string' || !/^\d{6}$/.test(otp)) return false;
    const a = Buffer.from(hashOtp(user.email, otp));
    const b = Buffer.from(user.otp_hash);
    if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return false;
    db.prepare('UPDATE users SET otp_hash=NULL, otp_expires=NULL, otp_attempts=0 WHERE id=?').run(user.id);
    return true;
  }

  const auth = (req, res, next) => {
    const h = req.headers.authorization || '';
    const token = h.startsWith('Bearer ') ? h.slice(7) : null;
    if (!token) return bad(res, 401, 'Not signed in', { code: 'NOT_SIGNED_IN' });
    try {
      const p = jwt.verify(token, secret, { algorithms: ['HS256'] });
      const u = db.prepare('SELECT id, email, verified FROM users WHERE id=?').get(p.uid);
      if (!u || !u.verified) return bad(res, 401, 'Session expired. Please sign in again.', { code: 'SESSION_EXPIRED' });
      req.user = u;
      next();
    } catch {
      return bad(res, 401, 'Session expired. Please sign in again.', { code: 'SESSION_EXPIRED' });
    }
  };

  app.get('/health', (req, res) => res.json({ ok: true }));

  app.post('/auth/register', authLimiter, wrap(async (req, res) => {
    const email = normEmail(req.body.email);
    const { password } = req.body;
    if (!EMAIL_RE.test(email) || email.length > 254) return bad(res, 400, 'Enter a valid email', { code: 'INVALID_EMAIL' });
    if (!validPassword(password)) return bad(res, 400, 'Password must be 8 to 72 characters', { code: 'WEAK_PASSWORD' });
    const existing = userByEmail(email);
    if (existing && existing.verified) return bad(res, 409, 'This email is already registered. Please sign in.', { code: 'EMAIL_EXISTS' });
    const hash = await bcrypt.hash(password, 11);
    let user = existing;
    if (existing) db.prepare('UPDATE users SET pass_hash=? WHERE id=?').run(hash, existing.id);
    else {
      const r = db.prepare('INSERT INTO users(email, pass_hash, created_at) VALUES (?,?,?)').run(email, hash, Date.now());
      user = db.prepare('SELECT * FROM users WHERE id=?').get(r.lastInsertRowid);
    }
    await issueOtp(user);
    res.json({ ok: true });
  }));

  app.post('/auth/otp', authLimiter, wrap(async (req, res) => {
    const user = userByEmail(normEmail(req.body.email));
    if (user) await issueOtp(user);
    res.json({ ok: true }); // same answer whether or not the account exists
  }));

  app.post('/auth/verify', authLimiter, wrap(async (req, res) => {
    const user = userByEmail(normEmail(req.body.email));
    if (!user || !checkOtp(user, req.body.otp)) return bad(res, 400, 'Invalid or expired code', { code: 'INVALID_CODE' });
    db.prepare('UPDATE users SET verified=1 WHERE id=?').run(user.id);
    res.json({ token: signToken(user) });
  }));

  app.post('/auth/reset', authLimiter, wrap(async (req, res) => {
    const user = userByEmail(normEmail(req.body.email));
    if (!validPassword(req.body.password)) return bad(res, 400, 'Password must be 8 to 72 characters', { code: 'WEAK_PASSWORD' });
    if (!user || !checkOtp(user, req.body.otp)) return bad(res, 400, 'Invalid or expired code', { code: 'INVALID_CODE' });
    const hash = await bcrypt.hash(req.body.password, 11);
    db.prepare('UPDATE users SET pass_hash=?, verified=1 WHERE id=?').run(hash, user.id);
    res.json({ token: signToken(user) });
  }));

  app.post('/auth/login', authLimiter, wrap(async (req, res) => {
    const user = userByEmail(normEmail(req.body.email));
    const pw = typeof req.body.password === 'string' ? req.body.password.slice(0, 72) : '';
    const ok = await bcrypt.compare(pw, user ? user.pass_hash : DUMMY_HASH);
    if (!user || !ok) return bad(res, 401, 'Invalid email or password', { code: 'INVALID_CREDENTIALS' });
    if (!user.verified) return bad(res, 403, 'Email not verified', { code: 'UNVERIFIED' });
    res.json({ token: signToken(user) });
  }));

  app.delete('/auth/account', authLimiter, auth, wrap(async (req, res) => {
    const user = db.prepare('SELECT * FROM users WHERE id=?').get(req.user.id);
    const ok = await bcrypt.compare(String(req.body.password || '').slice(0, 72), user.pass_hash);
    if (!ok) return bad(res, 401, 'Wrong password', { code: 'WRONG_PASSWORD' });
    db.prepare('DELETE FROM users WHERE id=?').run(user.id); // records cascade
    res.json({ ok: true });
  }));

  app.post('/sync', syncLimiter, auth, wrap(async (req, res) => {
    const { cursor, changes } = req.body;
    if (!Number.isInteger(cursor) || cursor < 0) return bad(res, 400, 'Invalid cursor', { code: 'INVALID_REQUEST' });
    if (!Array.isArray(changes) || changes.length > MAX_CHANGES) return bad(res, 400, 'Too many changes in one request', { code: 'INVALID_REQUEST' });
    const uid = req.user.id;

    for (const c of changes) {
      if (!c || !ENTITIES.has(c.entity) || typeof c.id !== 'string' || c.id.length === 0 || c.id.length > 64
        || !Number.isInteger(c.updatedAt) || typeof c.deleted !== 'boolean'
        || typeof c.data !== 'string' || Buffer.byteLength(c.data) > MAX_DATA_BYTES) return bad(res, 400, 'Invalid change', { code: 'INVALID_REQUEST' });
      let parsed;
      try { parsed = JSON.parse(c.data); } catch { return bad(res, 400, 'Invalid change data', { code: 'INVALID_REQUEST' }); }
      if (!parsed || parsed.id !== c.id) return bad(res, 400, 'Change id mismatch', { code: 'INVALID_REQUEST' });
    }

    const getRec = db.prepare('SELECT updated_at FROM records WHERE user_id=? AND entity=? AND id=?');
    const bump = db.prepare('UPDATE users SET seq = seq + 1 WHERE id=? RETURNING seq');
    const put = db.prepare(`INSERT INTO records(user_id, entity, id, updated_at, deleted, data, seq) VALUES (?,?,?,?,?,?,?)
      ON CONFLICT(user_id, entity, id) DO UPDATE SET updated_at=excluded.updated_at, deleted=excluded.deleted, data=excluded.data, seq=excluded.seq`);

    const accepted = new Set();
    const apply = db.transaction(() => {
      for (const c of changes) {
        const ex = getRec.get(uid, c.entity, c.id);
        if (!ex || c.updatedAt >= ex.updated_at) { // last write wins
          const { seq } = bump.get(uid);
          put.run(uid, c.entity, c.id, c.updatedAt, c.deleted ? 1 : 0, c.data, seq);
          accepted.add(`${c.entity}:${c.id}`);
        }
      }
    });
    apply();

    const rows = db.prepare('SELECT entity, id, updated_at, deleted, data, seq FROM records WHERE user_id=? AND seq>? ORDER BY seq LIMIT ?')
      .all(uid, cursor, PULL_LIMIT + 1);
    const hasMore = rows.length > PULL_LIMIT;
    const page = hasMore ? rows.slice(0, PULL_LIMIT) : rows;
    const userSeq = db.prepare('SELECT seq FROM users WHERE id=?').get(uid).seq;
    res.json({
      cursor: hasMore ? page[page.length - 1].seq : userSeq,
      hasMore,
      changes: page
        .filter((r) => !accepted.has(`${r.entity}:${r.id}`)) // don't echo what the device just sent
        .map((r) => ({ entity: r.entity, id: r.id, updatedAt: r.updated_at, deleted: !!r.deleted, data: r.data })),
    });
  }));

  app.use((req, res) => bad(res, 404, 'Not found', { code: 'NOT_FOUND' }));
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    if (err && err.type === 'entity.parse.failed') return bad(res, 400, 'Invalid JSON', { code: 'INVALID_REQUEST' });
    console.error(err);
    bad(res, 500, 'Server error', { code: 'SERVER_ERROR' });
  });
  return app;
}

module.exports = { createApp };
