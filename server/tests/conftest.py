import os

os.environ["ENVIRONMENT"] = "test"
os.environ["JWT_SECRET"] = "x" * 40
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"
# The whole test suite shares one "client IP" (ASGITransport), so the real 20/minute auth limit
# would eventually trip across unrelated tests as the suite grows. Rate limiting itself has its
# own dedicated tests (see test_otp_cooldown_blocks_immediate_resend); this just keeps everything
# else from tripping over a shared counter that has nothing to do with what each test is checking.
os.environ["AUTH_RATE_PER_MINUTE"] = "1000"

import pytest
from httpx import ASGITransport, AsyncClient

from app.database import Base, engine, init_models
from app.main import app
from app.mailer import OUTBOX


@pytest.fixture
async def client():
    # Fresh tables for every test. (SQLite doesn't enforce ON DELETE CASCADE by default, so without
    # this a deleted user's rows could leak into a later test via a reused user id.)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await init_models()
    OUTBOX.clear()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


def last_otp() -> str:
    return OUTBOX[-1]["otp"]