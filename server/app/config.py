from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


def _to_asyncpg(url: str) -> str:
    """Accept a plain Neon/Postgres URL (postgresql://...) and rewrite it to the asyncpg
    driver form SQLAlchemy needs (postgresql+asyncpg://...). Leaves sqlite/test URLs alone."""
    if url.startswith("postgresql://"):
        return "postgresql+asyncpg://" + url[len("postgresql://"):]
    if url.startswith("postgres://"):  # some providers use the short scheme
        return "postgresql+asyncpg://" + url[len("postgres://"):]
    return url


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite+aiosqlite:///./dev.db"
    jwt_secret: str = ""
    port: int = 8000
    environment: str = "development"
    trust_proxy: bool = False

    smtp_host: str = ""
    smtp_port: int = 587
    smtp_starttls: bool = True
    smtp_user: str = ""
    smtp_pass: str = ""
    mail_from: str = "ReceiptBook <no-reply@example.com>"

    auth_rate_per_minute: int = 20
    sync_rate_per_minute: int = 120

    @property
    def sqlalchemy_url(self) -> str:
        return _to_asyncpg(self.database_url)

    @property
    def is_postgres(self) -> bool:
        return self.sqlalchemy_url.startswith("postgresql")

    @property
    def is_production(self) -> bool:
        return self.environment == "production"

    @property
    def has_mailer(self) -> bool:
        return bool(self.smtp_host)


@lru_cache
def get_settings() -> Settings:
    return Settings()
