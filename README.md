# ReceiptBook – receipts, arrears & cash flow for small Pakistani businesses

Offline-first Android app (Kotlin + Jetpack Compose + Room) with a small Node.js backend used for
email sign-in and backup/sync between devices.

```
ReceiptBook/
├─ app/      Android app (open the ReceiptBook folder in Android Studio)
└─ server/   Node.js API (auth + sync)  – tested: `cd server && npm test`
```

## 1. Run the backend
```bash
cd server
npm install
cp .env.example .env            # set JWT_SECRET (openssl rand -hex 32)
export $(grep -v '^#' .env | xargs)
npm start                       # http://localhost:8080  (OTP codes print in the console until SMTP is set)
npm test                        # smoke tests (auth, OTP, sync, LWW, isolation, delete account)
```
Production: `NODE_ENV=production` requires SMTP_* settings. Put it behind HTTPS (nginx/Caddy) and set `TRUST_PROXY=1`.
Docker: `docker build -t receiptbook-api server && docker run -p 8080:8080 -v rb:/data --env-file server/.env receiptbook-api`

## 2. Run the app
1. Open the folder in **Android Studio (Koala or newer, JDK 17)**; let Gradle sync.
2. `gradle.properties`: `API_BASE_URL_DEBUG` is `http://10.0.2.2:8080` (emulator → your PC).
   On a real phone use your PC's LAN IP. For release set `API_BASE_URL_RELEASE=https://your-domain` (HTTPS only).
3. Run. Register → enter the emailed 6-digit code → create a business.

## 3. Features
| Area | What it does |
|---|---|
| Account | Email + password, email OTP verification, forgot password, account deletion |
| Businesses | Many per account: name, address, phone, suppliers, Cash/Credit terms, currency, receipt prefix, footer |
| Products | Price, unit, cost, supplier, optional stock tracking + low-stock alert |
| Customers | Phone, address, opening arrears, ledger, statement PDF, WhatsApp/share reminder |
| Receipt (order) | Pick/add customer or walk-in, tap products, custom items, discount, partial payment, **arrears shown**, extra money received goes to old arrears |
| Receipt output | Preview, share as **PDF** or **image (PNG)** (WhatsApp etc.) |
| History | Filter by date range, search, export **PDF / Excel** |
| Cancel | Receipts are never deleted; cancel keeps audit trail and restores stock |
| Cash flow | Payments received, expenses/supplier payments, net cash flow, top products, top debtors |
| Offline + sync | Everything works offline; background/foreground sync when online |

## 4. How money is calculated
`arrears = opening balance + Σ(bill total − paid on bill) [active bills] − Σ payments`.
Nothing is stored as a running balance, so it can never drift.

## 5. Security notes
* Session token in EncryptedSharedPreferences (Android Keystore); `allowBackup=false`.
* bcrypt passwords, HMAC-hashed OTPs (10 min expiry, 5 attempts, 60 s cooldown), rate limiting, helmet, strict input validation,
  JWT pinned to HS256, every sync row scoped to the JWT's user.
* Release builds allow HTTPS only.

## 6. Languages
The app ships in **English and Urdu** and is built so more languages are a drop-in, not a rewrite.

* **UI language**: switchable any time from the language icon on the sign-in screen, or Businesses → ⋮ → Language.
  Switching is instant (no restart) and includes right-to-left layout for Urdu/Arabic-script languages.
* **Receipt language**: set per business (Business settings → Receipt language), independent of the UI language —
  a shopkeeper can run the app in English but print Urdu receipts for customers, or the reverse.
  Receipts, PDFs and Excel exports render with correct Urdu letter-joining (via Android `StaticLayout`, not raw
  canvas text), right-to-left column order for RTL languages, and Latin (0-9) digits throughout, matching how
  amounts are written in Pakistani shops.
* **Error messages**: the backend returns machine-readable codes (`INVALID_CREDENTIALS`, `WEAK_PASSWORD`, …)
  instead of English text, so every error shown in the app is translated too.

### Adding a new language (e.g. Farsi, Hindi, Arabic)
1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml` and translate it
   (e.g. `values-fa` for Farsi, `values-hi` for Hindi, `values-ar` for Arabic).
2. Add one line in `app/src/main/java/com/receiptbook/app/i18n/Languages.kt`:
   `AppLanguage("fa", "فارسی", rtl = true)`.
3. Run `python3 tools/check_strings.py` — it fails loudly if any key is missing or a `%1$s`-style placeholder
   doesn't match, before you ship a crash.

That's the whole process: the language picker, RTL layout, receipts, PDF reports and Excel exports all pick up
the new language automatically. No other code changes are needed.

## 7. Known limitations / next steps
* Conflict rule is last-write-wins by device clock; stock quantity and receipt numbers can collide if two devices sell while offline
  (use one main device per business for now).
* Money uses Double rounded to 2 decimals (fine for PKR); switch to integer paisa if you add tax/returns.
* Not yet: Bluetooth thermal printer, staff logins/roles, app PIN/biometric lock, tax, partial returns, Play Store listing assets.
