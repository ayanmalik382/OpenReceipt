require('dotenv').config();
'use strict';
const nodemailer = require('nodemailer');

const transporter = process.env.SMTP_HOST
  ? nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: Number(process.env.SMTP_PORT || 587),
      secure: process.env.SMTP_SECURE === 'true',
      auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS },
    })
  : null;

const outbox = []; // used by tests

const hasMailer = () => !!transporter;

async function sendOtp(email, otp) {
  if (process.env.NODE_ENV === 'test') { outbox.push({ email, otp }); return; }
  if (!transporter) { console.warn(`[DEV ONLY] SMTP not configured. OTP for ${email}: ${otp}`); return; }
  await transporter.sendMail({
    from: process.env.MAIL_FROM || process.env.SMTP_USER,
    to: email,
    subject: 'Your ReceiptBook verification code',
    text: `Your ReceiptBook code is ${otp}. It expires in 10 minutes.\nIf you did not request this, you can ignore this email.`,
  });
}

module.exports = { sendOtp, hasMailer, outbox };
