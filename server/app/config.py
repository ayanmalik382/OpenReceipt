from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


# Query parameters that libpq-style clients (and Neon's "Connect" panel) put in the URL but that
# asyncpg rejects as unknown keyword arguments. TLS is switched on separately in database.py.
_UNSUPPORTED_QUERY_PARAMS = {"sslmode", "channel_binding", "sslrootcert", "sslcert", "sslkey", "options"}


def _to_asyncpg(url: str) -> str:
    """Accept the connection string exactly as Neon shows it (postgresql://...?sslmode=require&
    channel_binding=require) and turn it into what SQLAlchemy+asyncpg needs: the +asyncpg driver
    name and none of the libpq-only query parameters. Leaves sqlite/test URLs alone."""
    from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

    if url.startswith("postgres://"):  # some providers use the short scheme
        url = "postgresql://" + url[len("postgres://"):]
    if url.startswith("postgresql://"):
        url = "postgresql+asyncpg://" + url[len("postgresql://"):]
    if not url.startswith("postgresql+asyncpg://"):
        return url
    parts = urlsplit(url)
    keep = [(k, v) for k, v in parse_qsl(parts.query, keep_blank_values=True) if k.lower() not in _UNSUPPORTED_QUERY_PARAMS]
    return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(keep), parts.fragment))


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql://neondb_owner:npg_0U7MVJhzuPpl@ep-super-math-b42frfpi-pooler.c-6.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require"
    jwt_secret: str = "XbzhjzrzkPtBmobPSzwON8KHYzlJhP03ysknNjpPMza"
    port: int = 8000
    environment: str = "production"
    trust_proxy: bool = False

    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 587
    smtp_starttls: bool = True
    smtp_user: str = "ahsan.123muhammad@gmail.com"
    smtp_pass: str = "vvse njey djlq tjwe"
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