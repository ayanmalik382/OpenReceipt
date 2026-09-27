'use strict';
const assert = require('assert');
const Database = require('better-sqlite3');
const { createApp } = require('../src/app');
const { outbox } = require('../src/mailer');

const db = new Database(':memory:');
const app = createApp(db, 'x'.repeat(40));

(async () => {
  const server = app.listen(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, path, body, token) => {
    const r = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
    return { status: r.status, body: await r.json() };
  };
  const rec = (entity, id, updatedAt, extra = {}) => ({ entity, id, updatedAt, deleted: false, data: JSON.stringify({ id, updatedAt, ...extra }) });

  // register / verify / login
  let r = await call('POST', '/auth/register', { email: 'Owner@Test.com', password: 'short' });
  assert.equal(r.status, 400);
  r = await call('POST', '/auth/register', { email: 'Owner@Test.com', password: 'password123' });
  assert.equal(r.status, 200);
  r = await call('POST', '/auth/login', { email: 'owner@test.com', password: 'password123' });
  assert.equal(r.status, 403, 'unverified login blocked');
  assert.equal(r.body.code, 'UNVERIFIED');
  r = await call('POST', '/auth/verify', { email: 'owner@test.com', otp: '000000' });
  assert.equal(r.status, 400);
  const otp = outbox[outbox.length - 1].otp;
  r = await call('POST', '/auth/verify', { email: 'owner@test.com', otp });
  assert.equal(r.status, 200);
  r = await call('POST', '/auth/login', { email: 'owner@test.com', password: 'wrong-password' });
  assert.equal(r.status, 401);
  assert.equal(r.body.code, 'INVALID_CREDENTIALS', 'machine-readable error code for app translation');
  r = await call('POST', '/auth/login', { email: 'owner@test.com', password: 'password123' });
  assert.equal(r.status, 200);
  const tokenA = r.body.token;

  // sync auth required
  r = await call('POST', '/sync', { cursor: 0, changes: [] });
  assert.equal(r.status, 401);

  // device A pushes 2 rows
  r = await call('POST', '/sync', { cursor: 0, changes: [rec('business', 'b1', 100, { name: 'Shop' }), rec('product', 'p1', 100, { name: 'Rice' })] }, tokenA);
  assert.equal(r.status, 200);
  assert.equal(r.body.changes.length, 0, 'no echo');
  const cursorA = r.body.cursor;
  assert.ok(cursorA >= 2);

  // device B (cursor 0) pulls everything
  r = await call('POST', '/sync', { cursor: 0, changes: [] }, tokenA);
  assert.equal(r.body.changes.length, 2);

  // older update rejected (LWW), newer accepted
  r = await call('POST', '/sync', { cursor: cursorA, changes: [rec('product', 'p1', 50, { name: 'OLD' })] }, tokenA);
  r = await call('POST', '/sync', { cursor: 0, changes: [] }, tokenA);
  assert.equal(JSON.parse(r.body.changes.find((c) => c.id === 'p1').data).name, 'Rice');
  await call('POST', '/sync', { cursor: cursorA, changes: [rec('product', 'p1', 200, { name: 'Basmati' })] }, tokenA);
  r = await call('POST', '/sync', { cursor: 0, changes: [] }, tokenA);
  assert.equal(JSON.parse(r.body.changes.find((c) => c.id === 'p1').data).name, 'Basmati');

  // validation
  r = await call('POST', '/sync', { cursor: 0, changes: [{ entity: 'evil', id: 'x', updatedAt: 1, deleted: false, data: '{"id":"x"}' }] }, tokenA);
  assert.equal(r.status, 400);
  r = await call('POST', '/sync', { cursor: 0, changes: [rec('product', 'p9', 1)].map((c) => ({ ...c, id: 'other' })) }, tokenA);
  assert.equal(r.status, 400, 'id mismatch');

  // isolation between users
  await call('POST', '/auth/register', { email: 'other@test.com', password: 'password123' });
  await call('POST', '/auth/verify', { email: 'other@test.com', otp: outbox[outbox.length - 1].otp });
  r = await call('POST', '/auth/login', { email: 'other@test.com', password: 'password123' });
  const tokenB = r.body.token;
  r = await call('POST', '/sync', { cursor: 0, changes: [] }, tokenB);
  assert.equal(r.body.changes.length, 0, 'user B sees nothing of user A');

  // password reset
  await call('POST', '/auth/otp', { email: 'other@test.com' }); // cooldown: silently ignored
  r = await call('POST', '/auth/reset', { email: 'other@test.com', otp: '123456', password: 'newpassword1' });
  assert.equal(r.status, 400);

  // account deletion
  r = await call('DELETE', '/auth/account', { password: 'nope' }, tokenB);
  assert.equal(r.status, 401);
  r = await call('DELETE', '/auth/account', { password: 'password123' }, tokenB);
  assert.equal(r.status, 200);
  r = await call('POST', '/sync', { cursor: 0, changes: [] }, tokenB);
  assert.equal(r.status, 401);

  server.close();
  console.log('ALL SERVER TESTS PASSED');
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
