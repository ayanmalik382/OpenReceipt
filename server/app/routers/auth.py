import re

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..config import get_settings
from ..database import get_db
from ..deps import get_current_user, limiter
from ..mailer import send_otp
from ..models import User
from ..schemas import DeleteAccountIn, LoginIn, OkOut, OtpIn, RegisterIn, ResetIn, TokenOut, VerifyIn
from ..security import (
    MAX_OTP_ATTEMPTS,
    OTP_COOLDOWN_SECONDS,
    OTP_TTL_SECONDS,
    hash_otp,
    hash_password,
    new_otp,
    now_ms,
    otp_matches,
    sign_token,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])
settings = get_settings()
EMAIL_RE = re.compile(r"^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$")


def bad(status: int, message: str, code: str):
    raise HTTPException(status_code=status, detail={"error": message, "code": code})


def norm_email(email: str) -> str:
    return email.strip().lower()


async def _user_by_email(db: AsyncSession, email: str) -> User | None:
    return (await db.execute(select(User).where(User.email == email))).scalar_one_or_none()


async def _issue_otp(db: AsyncSession, user: User) -> bool:
    now = now_ms()
    if settings.environment != "test" and user.otp_sent_at and now - user.otp_sent_at < OTP_COOLDOWN_SECONDS * 1000:
        return False
    otp = new_otp()
    user.otp_hash = hash_otp(user.email, otp)
    user.otp_expires = now + OTP_TTL_SECONDS * 1000
    user.otp_attempts = 0
    user.otp_sent_at = now
    await db.commit()
    await send_otp(user.email, otp)
    return True


async def _check_otp(db: AsyncSession, user: User | None, otp: str) -> bool:
    if user is None or not user.otp_hash or not user.otp_expires or now_ms() > user.otp_expires:
        return False
    if user.otp_attempts >= MAX_OTP_ATTEMPTS:
        return False
    user.otp_attempts += 1
    await db.commit()
    if not otp_matches(user.email, otp, user.otp_hash):
        return False
    user.otp_hash = None
    user.otp_expires = None
    user.otp_attempts = 0
    await db.commit()
    return True


@router.post("/register", response_model=OkOut)
@limiter.limit(f"{settings.auth_rate_per_minute}/minute")
async def register(request: Request, body: RegisterIn, db: AsyncSession = Depends(get_db)):
    email = norm_email(body.email)
    if not EMAIL_RE.match(email) or len(email) > 254:
        bad(400, "Enter a valid email", "INVALID_EMAIL")
    if not (8 <= len(body.password) <= 72):
        bad(400, "Password must be 8 to 72 characters", "WEAK_PASSWORD")

    existing = await _user_by_email(db, email)
    if existing and existing.verified:
        bad(409, "This email is already registered. Please sign in.", "EMAIL_EXISTS")

    pass_hash = hash_password(body.password)
    if existing:
        existing.pass_hash = pass_hash
        user = existing
    else:
        user = User(email=email, pass_hash=pass_hash, verified=False, created_at=now_ms())
        db.add(user)
    await db.commit()
    await db.refresh(user)
    await _issue_otp(db, user)
    return OkOut()


@router.post("/otp", response_model=OkOut)
@limiter.limit(f"{settings.auth_rate_per_minute}/minute")
async def resend_otp(request: Request, body: OtpIn, db: AsyncSession = Depends(get_db)):
    user = await _user_by_email(db, norm_email(body.email))
    if user:
        await _issue_otp(db, user)
    return OkOut()  # same response whether or not the account exists


@router.post("/verify", response_model=TokenOut)
@limiter.limit(f"{settings.auth_rate_per_minute}/minute")
async def verify(request: Request, body: VerifyIn, db: AsyncSession = Depends(get_db)):
    user = await _user_by_email(db, norm_email(body.email))
    if not await _check_otp(db, user, body.otp.strip()):
        bad(400, "Invalid or expired code", "INVALID_CODE")
    user.verified = True
    await db.commit()
    return TokenOut(token=sign_token(user.id))


@router.post("/reset", response_model=TokenOut)
@limiter.limit(f"{settings.auth_rate_per_minute}/minute")
async def reset_password(request: Request, body: ResetIn, db: AsyncSession = Depends(get_db)):
    if not (8 <= len(body.password) <= 72):
        bad(400, "Password must be 8 to 72 characters", "WEAK_PASSWORD")
    user = await _user_by_email(db, norm_email(body.email))
    if not await _check_otp(db, user, body.otp.strip()):
        bad(400, "Invalid or expired code", "INVALID_CODE")
    user.pass_hash = hash_password(body.password)
    user.verified = True
    await db.commit()
    return TokenOut(token=sign_token(user.id))


@router.post("/login", response_model=TokenOut)
@limiter.limit(f"{settings.auth_rate_per_minute}/minute")
async def login(request: Request, body: LoginIn, db: AsyncSession = Depends(get_db)):
    user = await _user_by_email(db, norm_email(body.email))
    ok = verify_password(body.password, user.pass_hash if user else None)
    if not user or not ok:
        bad(401, "Invalid email or password", "INVALID_CREDENTIALS")
    if not user.verified:
        bad(403, "Please verify your email first", "UNVERIFIED")
    return TokenOut(token=sign_token(user.id))


@router.delete("/account", response_model=OkOut)
@limiter.limit(f"{settings.auth_rate_per_minute}/minute")
async def delete_account(
    request: Request, body: DeleteAccountIn, db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)
):
    if not verify_password(body.password, user.pass_hash):
        bad(401, "Wrong password", "WRONG_PASSWORD")
    await db.delete(user)  # cascades to records
    await db.commit()
    return OkOut()
