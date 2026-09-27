import logging
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi.errors import RateLimitExceeded

from .config import get_settings
from .database import init_models
from .deps import limiter
from .mailer import send_otp  # noqa: F401  (imported so mailer config errors surface at import time)
from .routers import auth, sync

log = logging.getLogger("receiptbook")
settings = get_settings()

if not settings.jwt_secret or len(settings.jwt_secret) < 32:
    print("FATAL: set JWT_SECRET to 32+ random characters. Example: openssl rand -hex 32", file=sys.stderr)
    sys.exit(1)
if settings.is_production and not settings.has_mailer:
    print("FATAL: SMTP_* variables are required when ENVIRONMENT=production (OTP emails).", file=sys.stderr)
    sys.exit(1)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # create_all only adds tables that don't exist yet, so this is safe to run on every
    # boot - including against Neon. For a larger schema, switch to Alembic migrations.
    await init_models()
    yield


app = FastAPI(title="ReceiptBook API", lifespan=lifespan)
app.state.limiter = limiter
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(status_code=429, content={"error": "Too many requests. Please wait a minute.", "code": "RATE_LIMITED"})


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    detail = exc.detail
    if isinstance(detail, dict) and "error" in detail:
        return JSONResponse(status_code=exc.status_code, content=detail)
    return JSONResponse(status_code=exc.status_code, content={"error": str(detail), "code": "INVALID_REQUEST"})


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(status_code=400, content={"error": "Something went wrong. Please try again.", "code": "INVALID_REQUEST"})


@app.get("/health")
async def health():
    return {"ok": True}


app.include_router(auth.router)
app.include_router(sync.router)
