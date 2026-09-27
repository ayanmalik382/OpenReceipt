import json
import re

from pydantic import BaseModel, Field, field_validator

EMAIL_RE = re.compile(r"^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$")
ALLOWED_ENTITIES = {"business", "supplier", "product", "customer", "order", "orderItem", "payment", "expense"}


class RegisterIn(BaseModel):
    email: str
    password: str


class OtpIn(BaseModel):
    email: str


class VerifyIn(BaseModel):
    email: str
    otp: str


class LoginIn(BaseModel):
    email: str
    password: str


class ResetIn(BaseModel):
    email: str
    otp: str
    password: str


class DeleteAccountIn(BaseModel):
    password: str


class TokenOut(BaseModel):
    token: str


class OkOut(BaseModel):
    ok: bool = True


class ChangeIn(BaseModel):
    entity: str
    id: str = Field(min_length=1, max_length=64)
    updatedAt: int
    deleted: bool
    data: str = Field(max_length=100_000)

    @field_validator("entity")
    @classmethod
    def entity_allowed(cls, v: str) -> str:
        if v not in ALLOWED_ENTITIES:
            raise ValueError("invalid entity")
        return v

    @field_validator("data")
    @classmethod
    def data_matches_id(cls, v: str, info):
        try:
            parsed = json.loads(v)
        except ValueError:
            raise ValueError("invalid change data")
        if not isinstance(parsed, dict) or parsed.get("id") != info.data.get("id"):
            raise ValueError("change id mismatch")
        return v


class SyncIn(BaseModel):
    cursor: int = Field(ge=0)
    changes: list[ChangeIn] = Field(max_length=500)


class ChangeOut(BaseModel):
    entity: str
    id: str
    updatedAt: int
    deleted: bool
    data: str


class SyncOut(BaseModel):
    cursor: int
    hasMore: bool
    changes: list[ChangeOut]
