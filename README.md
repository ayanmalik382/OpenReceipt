# ReceiptBook – receipts, arrears & cash flow for small Pakistani businesses

Offline-first Android app (Kotlin + Jetpack Compose + Room). **The app works fully with no
account at all** — signing in is entirely optional and only adds cloud backup, via a small
Python (FastAPI) backend designed to run against a **Neon** PostgreSQL database.

```
ReceiptBook/
├─ app/                  Android app (open the ReceiptBook folder in Android Studio)
├─ server/               Python FastAPI + PostgreSQL/Neon backend (auth + backup/sync)
└─ server-node-legacy/   The original Node.js backend - kept for reference, no longer used
```

## 1. Run the backend
```bash
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env        # set DATABASE_URL (from Neon) and JWT_SECRET (openssl rand -hex 32)
export $(grep -v '^#' .env | xargs)
uvicorn app.main:app --reload --port 8000
pytest -q                   # 10 tests, run against an in-memory SQLite DB, not Neon
```
Tables are created automatically on first boot against Neon, same as SQLite — nothing to migrate
by hand. OTP codes print to the console until SMTP is configured. Full setup (creating the Neon
project, deploying with Docker, etc.) is in `server/README.md`.

## 2. Run the app
1. Open the folder in **Android Studio (Koala or newer, JDK 17)**; let Gradle sync.
2. `gradle.properties`: `API_BASE_URL_DEBUG` is `http://10.0.2.2:8000` (emulator → your PC, matching
   the FastAPI default port). On a real phone use your PC's LAN IP. For release set
   `API_BASE_URL_RELEASE=https://your-domain` (HTTPS only).
3. Run. The app opens straight into "My Businesses" — no sign-in required. Tap "Add business" and
   start using it immediately.

## 3. Features
| Area | What it does |
|---|---|
| Account (optional) | Email + password, email OTP verification, forgot password, account deletion. Signing in only adds backup — every screen works fully without it. |
| Businesses | Many per device: name, address, phone, suppliers, Cash/Credit terms, currency, receipt prefix, footer, receipt template, receipt language |
| Products | Price, unit, cost, supplier, optional stock tracking + low-stock alert |
| Customers | Phone, address, opening arrears, ledger, statement PDF, WhatsApp/share reminder |
| Receipt (order) | Pick/add customer or walk-in, tap products, custom items, discount, partial payment, **arrears shown**, extra money received goes to old arrears |
| Receipt templates | 4 predefined looks (Classic, Modern, Compact, Wide/Printer) plus a fully custom template (color, paper size, dividers, logo badge), chosen per business and changeable any time |
| Receipt output | Preview, share as **PDF** or **image (PNG)** (WhatsApp etc.), in the template and language the business has chosen |
| History | Filter by date range, search, export **PDF / Excel** |
| Cancel | Receipts are never deleted; cancel keeps audit trail and restores stock |
| Cash flow | Payments received, expenses/supplier payments, net cash flow, top products, top debtors |
| Offline + optional sync | Everything works offline, permanently, with no account. Signing in backs the same data up and syncs it to other signed-in devices. |
| Logo & photos | A business logo (home screen, every receipt, every PDF report) and optional customer/supplier photos. Any picture is auto-rotated, resized and compressed to ~100KB before it's stored. |
| Unpaid-bills check | Customer screen lists exactly which bills still have money owing (later payments applied oldest-first); the New Receipt screen shows the count as soon as a customer is picked. |
| Customer reports | Reports → "Reports by customer": pick any customer, see billed/paid/balance for the chosen date range (with balance brought forward), export as PDF or Excel. |
| Export anywhere | Every export (receipt PDF/image, report PDFs, Excel) offers **Share** or **Save to device** (Downloads/ReceiptBook). |
| App icon | Businesses ⋮ → "App icon": choose from 4 bundled designs. |
| Languages | English and Urdu (app UI and, separately, receipt language), instant switch, full RTL support |

## 4. How money is calculated
`arrears = opening balance + Σ(bill total − paid on bill) [active bills] − Σ payments`.
Nothing is stored as a running balance, so it can never drift.

## 5. Offline-first, optional login
The local database is always fully usable — creating businesses, receipts, customers, everything —
whether or not anyone has ever signed in on this device. Signing in adds nothing to what you can do
locally; it only starts backing the same data up to your account, so it survives a lost phone and
can appear on a second device.

* **Sign in from**: Businesses → ⋮ → "Sign in to back up" (the same screen's back arrow doubles
  as "continue without an account" when reached that way).
* **Sign out**: only removes the account link on this device. Nothing local is deleted — your
  businesses and receipts stay exactly as they are, still fully usable offline.
* **Delete account**: the one action that does wipe local data too, since it's an explicit,
  password-confirmed "delete everything" request.
* Data created before signing in is not filtered out or hidden once you do sign in — it becomes
  part of your account the next time sync runs (dirty rows are pushed regardless of when they were
  created). There's no separate "claim" step.

## 6. Receipt templates
Business settings → **Receipt template** opens a gallery of 4 predefined looks and a **Custom**
option (accent color, paper width — 58mm/80mm thermal or A4 — divider style, bold header, an
optional logo-initial badge, and compact spacing). Tapping any template's thumbnail shows a full
sample receipt rendered with real content before you commit to it. The template is a property of
the *business*, not the app, so two businesses on the same device/account can look completely
different, and it can be changed at any time with no effect on past receipts (each receipt is
rendered fresh, in the business's *current* template, when opened or exported).

Templates and languages are independent: a business's receipt renders in whatever language it has
chosen (see below) inside whatever template it has chosen — an Urdu receipt looks correct in every
template, including the Wide/A4 layout.

## 7. Languages
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

## 8. Security notes
* Session token in EncryptedSharedPreferences (Android Keystore); `allowBackup=false`.
* bcrypt passwords, HMAC-hashed OTPs (10 min expiry, 5 attempts, 60 s cooldown), rate limiting,
  strict input validation, JWT pinned to HS256, every sync row scoped by the JWT's user id
  (SQLAlchemy + parameterized queries throughout — no hand-built SQL).
* Release builds allow HTTPS only.

## 9. Known limitations / next steps
* Conflict rule is last-write-wins by device clock; stock quantity and receipt numbers can collide if two devices sell while offline
  (use one main device per business for now).
* Money uses Double rounded to 2 decimals (fine for PKR); switch to integer paisa if you add tax/returns.
* Signing in on the same device with a *different* account will show all of this device's local
  businesses regardless of which account is active — there's no per-account local data separation
  (by design: local data belongs to the device first, the account is just a backup target).
* Rate limiting on the Python backend is in-memory per process — fine for one instance; add a
  Redis-backed limiter if you ever run more than one API instance behind a load balancer.
* The logo appears on receipts (PDF/PNG) and all PDF reports, but **not inside Excel files** - embedding pictures in
  the hand-written .xlsx writer is a sizeable job of its own; Excel exports carry the business name only.
* Changing the app icon uses Android's activity-alias mechanism. The new icon shows immediately on most phones, but some
  launchers cache icons or briefly restart the app when it changes.
* Photos/logos are stored inside each synced record as Base64 (simple, fits the existing sync). That's fine for a few
  hundred customers; if you ever expect thousands of photos, move them to object storage instead.
* Not yet: Bluetooth thermal printer, staff logins/roles, app PIN/biometric lock, tax, partial returns, Play Store listing assets.
