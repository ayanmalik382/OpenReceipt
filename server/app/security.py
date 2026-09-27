import hashlib
import hmac
import secrets
import time

import bcrypt
import jwt

from .config import get_settings

settings = get_settings()

OTP_TTL_SECONDS = 10 * 60
OTP_COOLDOWN_SECONDS = 60
MAX_OTP_ATTEMPTS = 5

# Used for a constant-time "user not found" login check, so the response time doesn't
# leak whether an email is registered.
_DUMMY_HASH = bcrypt.hashpw(b"dummy-password-for-constant-time", bcrypt.gensalt(rounds=11)).decode()


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode()[:72], bcrypt.gensalt(rounds=11)).decode()


def verify_password(password: str, hashed: str | None) -> bool:
    target = hashed or _DUMMY_HASH
    try:
        return bcrypt.checkpw(password.encode()[:72], target.encode())
    except ValueError:
        return False


def now_ms() -> int:
    return int(time.time() * 1000)


def sign_token(user_id: int) -> str:
    payload = {"uid": user_id, "exp": int(time.time()) + 30 * 24 * 3600}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def decode_token(token: str) -> int | None:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        return int(payload["uid"])
    except (jwt.PyJWTError, KeyError, ValueError):
        return None


def new_otp() -> str:
    return f"{secrets.randbelow(900000) + 100000}"


def hash_otp(email: str, otp: str) -> str:
    return hmac.new(settings.jwt_secret.encode(), f"{email}:{otp}".encode(), hashlib.sha256).hexdigest()


def otp_matches(email: str, otp: str, otp_hash: str) -> bool:
    if not otp or len(otp) != 6 or not otp.isdigit():
        return False
    return hmac.compare_digest(hash_otp(email, otp), otp_hash)
