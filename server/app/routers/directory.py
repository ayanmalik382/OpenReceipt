from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import delete, or_, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.ext.asyncio import AsyncSession

from ..config import get_settings
from ..database import get_db
from ..deps import get_current_user, limiter
from ..models import DirectoryListing, User
from ..schemas import DirectoryEntryOut, DirectoryPublishIn, DirectorySearchOut, OkOut
from ..security import now_ms

router = APIRouter(prefix="/directory", tags=["directory"])
settings = get_settings()
SEARCH_LIMIT_MAX = 50


def _insert_for(session: AsyncSession):
    return pg_insert if session.bind.dialect.name == "postgresql" else sqlite_insert


@router.put("/{business_id}", response_model=OkOut)
@limiter.limit(f"{settings.sync_rate_per_minute}/minute")
async def publish(
    request: Request, business_id: str, body: DirectoryPublishIn,
    db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)
):
    """Publishes (or updates) this business's directory listing. A person can only ever publish
    their OWN businesses - business_id is just this device's local id for it, used as the row's
    primary key so publishing the same business again updates the same listing instead of
    duplicating it."""
    insert = _insert_for(db)
    stmt = insert(DirectoryListing).values(
        id=business_id, owner_user_id=user.id, name=body.name, city=body.city, field=body.field,
        nature=body.nature, phone=body.phone, address=body.address, updated_at=now_ms(),
    )
    stmt = stmt.on_conflict_do_update(
        index_elements=[DirectoryListing.id],
        set_={
            "owner_user_id": stmt.excluded.owner_user_id, "name": stmt.excluded.name, "city": stmt.excluded.city,
            "field": stmt.excluded.field, "nature": stmt.excluded.nature, "phone": stmt.excluded.phone,
            "address": stmt.excluded.address, "updated_at": stmt.excluded.updated_at,
        },
        # A person can only update their OWN listing - this silently no-ops rather than erroring
        # if business_id somehow collided with someone else's (local ids are random UUIDs, so in
        # practice this never happens, but ownership must never be able to change by accident).
        where=(DirectoryListing.owner_user_id == user.id),
    )
    await db.execute(stmt)
    await db.commit()
    return OkOut()


@router.delete("/{business_id}", response_model=OkOut)
@limiter.limit(f"{settings.sync_rate_per_minute}/minute")
async def unpublish(request: Request, business_id: str, db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    await db.execute(delete(DirectoryListing).where(DirectoryListing.id == business_id, DirectoryListing.owner_user_id == user.id))
    await db.commit()
    return OkOut()


@router.get("", response_model=DirectorySearchOut)
@limiter.limit(f"{settings.sync_rate_per_minute}/minute")
async def search(
    request: Request,
    field: str | None = None, nature: str | None = None, city: str | None = None, q: str | None = None,
    limit: int = Query(default=20, ge=1, le=SEARCH_LIMIT_MAX), offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user),
):
    """Finds OTHER people's published businesses - never the caller's own (use the normal
    per-device business list for that)."""
    stmt = select(DirectoryListing).where(DirectoryListing.owner_user_id != user.id)
    if field:
        stmt = stmt.where(DirectoryListing.field == field)
    if nature:
        stmt = stmt.where(DirectoryListing.nature == nature)
    if city:
        stmt = stmt.where(DirectoryListing.city.ilike(f"%{city.strip()}%"))
        # stmt = stmt.where(DirectoryListing.city.ilike(city))
    if q:
        like = f"%{q.strip()}%"
        stmt = stmt.where(or_(DirectoryListing.name.ilike(like), DirectoryListing.city.ilike(like)))
    stmt = stmt.order_by(DirectoryListing.updated_at.desc()).offset(offset).limit(limit + 1)
    rows = (await db.execute(stmt)).scalars().all()
    has_more = len(rows) > limit
    page = rows[:limit] if has_more else rows
    return DirectorySearchOut(
        results=[DirectoryEntryOut(id=r.id, name=r.name, city=r.city, field=r.field, nature=r.nature, phone=r.phone, address=r.address) for r in page],
        hasMore=has_more,
    )