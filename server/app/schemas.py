import json
import re

from pydantic import BaseModel, Field, field_validator

EMAIL_RE = re.compile(r"^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$")
ALLOWED_ENTITIES = {"business", "supplier", "product", "customer", "order", "orderItem", "payment", "expense"}

PURGEABLE_ENTITIES = {"business", "supplier", "product", "customer", "payment", "expense"}


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
    data: str = Field(max_length=512_000)  # rows can carry a Base64 logo/photo

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


# ---------------------------------------------------------------- Business directory

class DirectoryPublishIn(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    city: str = Field(min_length=1, max_length=100)
    field: str = Field(min_length=1, max_length=100)
    nature: str = Field(min_length=1, max_length=100)
    phone: str = Field(default="", max_length=32)
    address: str = Field(default="", max_length=300)


class DirectoryEntryOut(BaseModel):
    id: str
    name: str
    city: str
    field: str
    nature: str
    phone: str
    address: str
    # Always true: only an entry a signed-in owner explicitly published can exist at all, so
    # everything returned by search is "verified" by definition - see models.DirectoryListing.
    verified: bool = True


class DirectorySearchOut(BaseModel):
    results: list[DirectoryEntryOut]
    hasMore: bool