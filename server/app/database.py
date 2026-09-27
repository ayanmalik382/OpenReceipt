from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.pool import StaticPool

from .config import get_settings

settings = get_settings()


class Base(DeclarativeBase):
    pass


def _make_engine():
    url = settings.sqlalchemy_url
    if url.startswith("sqlite"):
        # In-memory SQLite (used by the test suite) needs one shared connection, or every
        # session sees an empty database.
        return create_async_engine(
            url, connect_args={"check_same_thread": False}, poolclass=StaticPool
        )
    # Neon (and most managed Postgres) require TLS; asyncpg wants it as a connect arg,
    # not a "?sslmode=require" query string.
    return create_async_engine(url, connect_args={"ssl": True}, pool_pre_ping=True)


engine = _make_engine()
SessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


async def get_db():
    async with SessionLocal() as session:
        yield session


async def init_models():
    """Creates tables if they don't exist yet. Simple and idempotent - fine at this scale.
    For a larger project, switch to Alembic migrations instead of create_all."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
