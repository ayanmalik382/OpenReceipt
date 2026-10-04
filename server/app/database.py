from sqlalchemy import event
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
        engine = create_async_engine(
            url, connect_args={"check_same_thread": False}, poolclass=StaticPool
        )
        # SQLite ignores ON DELETE CASCADE (and all other foreign-key enforcement) unless this
        # pragma is set on every single connection - it is NOT the default, unlike Postgres/Neon.
        # Without this, deleting an account would silently leave its directory_listings/records
        # rows behind on SQLite (it already worked correctly on the real Neon/Postgres backend).
        @event.listens_for(engine.sync_engine, "connect")
        def _enable_sqlite_foreign_keys(dbapi_connection, _):
            cursor = dbapi_connection.cursor()
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.close()

        return engine
    # Neon (and most managed Postgres) require TLS; asyncpg wants it as a connect arg,
    # not a "?sslmode=require" query string.
    # Neon's "-pooler" hostnames go through PgBouncer, which doesn't reliably support asyncpg's
    # prepared-statement cache - turning the cache off avoids "prepared statement ... does not
    # exist" errors. (Harmless on a direct, non-pooled connection.)
    return create_async_engine(
        url,
        connect_args={"ssl": True, "statement_cache_size": 0, "prepared_statement_cache_size": 0},
        pool_pre_ping=True,
    )


engine = _make_engine()
SessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


async def get_db():
    async with SessionLocal() as session:
        yield session


async def init_models():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)