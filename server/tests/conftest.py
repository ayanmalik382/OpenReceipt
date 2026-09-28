import os

os.environ["ENVIRONMENT"] = "test"
os.environ["JWT_SECRET"] = "x" * 40
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"

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
