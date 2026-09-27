'use strict';
const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const { createApp } = require('./app');
const { hasMailer } = require('./mailer');

const secret = process.env.JWT_SECRET;
if (!secret || secret.length < 32) {
  console.error('FATAL: set JWT_SECRET (32+ chars). Example: openssl rand -hex 32');
  process.exit(1);
}
if (process.env.NODE_ENV === 'production' && !hasMailer()) {
  console.error('FATAL: SMTP_* variables are required in production (OTP emails).');
  process.exit(1);
}
const dbPath = process.env.DB_PATH || './data/receiptbook.db';
fs.mkdirSync(path.dirname(dbPath), { recursive: true });
const db = new Database(dbPath);
const app = createApp(db, secret);
const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`ReceiptBook API listening on :${port}`));
