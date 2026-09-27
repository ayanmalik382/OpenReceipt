# ReceiptBook API (Python / FastAPI / PostgreSQL)

Same job as before (email OTP sign-in, JWT sessions, offline-sync endpoint), rewritten in
FastAPI with an async SQLAlchemy + PostgreSQL backend, built to run against **Neon**.

## 1. Create a Neon database
1. Sign up at https://neon.tech and create a project (free tier is enough to start).
2. Open the project's **Connect** panel and copy the connection string. It looks like:
   `postgresql://user:password@ep-xxxx-xxxx.region.aws.neon.tech/dbname`
3. Paste it into `.env` as `DATABASE_URL` exactly like that (`postgresql://...`) — this app
   rewrites it to the asyncpg driver form itself, and adds the TLS flag Neon requires.

## 2. Run it locally
```bash
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt   # requirements.txt + test tools
cp .env.example .env                  # fill in DATABASE_URL and JWT_SECRET
export $(grep -v '^#' .env | xargs)
uvicorn app.main:app --reload --port 8000
pytest -q                             # runs against an in-memory SQLite DB, not Neon
```
Tables are created automatically on first boot (`create_all`), against Neon or SQLite alike —
there's no separate migration step to run. OTP codes print to the console until SMTP is set.

## 3. Deploy
```bash
docker build -t receiptbook-api server
docker run -p 8000:8000 --env-file server/.env receiptbook-api
```
Works on any host that can run a container and reach Neon over the internet (Railway, Render,
Fly.io, a plain VPS, …). No persistent volume is needed here — Neon holds the data, not the
container — which is the main practical difference from the old SQLite-file version.

Put HTTPS in front of it (Caddy, nginx, or your platform's built-in HTTPS) and set
`API_BASE_URL_RELEASE` in the Android app's `gradle.properties` to that HTTPS URL.

## 4. API contract
Identical to the previous Node backend, so the Android app needs no networking changes:
`POST /auth/register|otp|verify|login|reset`, `DELETE /auth/account`, `POST /sync`. Errors are
`{"error": "...", "code": "SOME_CODE"}`; the app already translates codes like
`INVALID_CREDENTIALS` into the user's chosen language.

## 5. Notes
* `create_all` is fine at this size. If the schema grows a lot, switch to Alembic migrations.
* Rate limiting (`slowapi`) is in-memory per process — correct for one instance; add Redis-backed
  storage if you ever run more than one API instance behind a load balancer.
* The old Node/SQLite backend is kept at `../server-node-legacy/` for reference only; it is not
  used by the app anymore.
