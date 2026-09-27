from fastapi import Depends, Header, HTTPException
from slowapi import Limiter
from sqlalchemy.ext.asyncio import AsyncSession

from .config import get_settings
from .database import get_db
from .models import User
from .security import decode_token

settings = get_settings()


def _client_key(request) -> str:
    if settings.trust_proxy:
        fwd = request.headers.get("x-forwarded-for")
        if fwd:
            return fwd.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


limiter = Limiter(key_func=_client_key)


async def get_current_user(
    authorization: str | None = Header(default=None),
    db: AsyncSession = Depends(get_db),
) -> User:
    token = None
    if authorization and authorization.startswith("Bearer "):
        token = authorization[len("Bearer "):]
    uid = decode_token(token) if token else None
    if uid is None:
        raise HTTPException(status_code=401, detail={"error": "Session expired. Please sign in again.", "code": "SESSION_EXPIRED"})
    user = await db.get(User, uid)
    if user is None or not user.verified:
        raise HTTPException(status_code=401, detail={"error": "Session expired. Please sign in again.", "code": "SESSION_EXPIRED"})
    return user
