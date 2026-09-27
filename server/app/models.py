from sqlalchemy import BigInteger, Boolean, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    email: Mapped[str] = mapped_column(String(254), unique=True, nullable=False, index=True)
    pass_hash: Mapped[str] = mapped_column(String(100), nullable=False)
    verified: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    otp_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)
    otp_expires: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    otp_attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    otp_sent_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)

    # Per-user change counter used as the sync cursor. Bumped once per accepted change.
    seq: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    created_at: Mapped[int] = mapped_column(BigInteger, nullable=False)


class Record(Base):
    """One row per synced entity (business/product/customer/order/...). Composite primary
    key mirrors the SQLite version: a device can never accidentally create a duplicate."""
    __tablename__ = "records"

    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True
    )
    entity: Mapped[str] = mapped_column(String(32), primary_key=True)
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    updated_at: Mapped[int] = mapped_column(BigInteger, nullable=False)
    deleted: Mapped[bool] = mapped_column(Boolean, nullable=False)
    data: Mapped[str] = mapped_column(Text, nullable=False)
    seq: Mapped[int] = mapped_column(BigInteger, nullable=False)

    __table_args__ = (Index("ix_records_user_seq", "user_id", "seq"),)
