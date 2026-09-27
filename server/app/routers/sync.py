from fastapi import APIRouter, Depends, Request
from sqlalchemy import select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.ext.asyncio import AsyncSession

from ..config import get_settings
from ..database import get_db
from ..deps import get_current_user, limiter
from ..models import Record, User
from ..schemas import ChangeOut, SyncIn, SyncOut

router = APIRouter(tags=["sync"])
settings = get_settings()

PULL_LIMIT = 1000


def _insert_for(session: AsyncSession):
    return pg_insert if session.bind.dialect.name == "postgresql" else sqlite_insert


@router.post("/sync", response_model=SyncOut)
@limiter.limit(f"{settings.sync_rate_per_minute}/minute")
async def sync(request: Request, body: SyncIn, db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    accepted: set[tuple[str, str]] = set()

    if body.changes:
        # Reserve a contiguous block of sequence numbers in one atomic step, then hand
        # them out in order - far cheaper than one round trip per change.
        n = len(body.changes)
        new_seq = (
            await db.execute(update(User).where(User.id == user.id).values(seq=User.seq + n).returning(User.seq))
        ).scalar_one()
        start_seq = new_seq - n + 1

        insert = _insert_for(db)
        for i, change in enumerate(body.changes):
            stmt = insert(Record).values(
                user_id=user.id, entity=change.entity, id=change.id,
                updated_at=change.updatedAt, deleted=change.deleted, data=change.data, seq=start_seq + i,
            )
            # Last-write-wins: only replace an existing row if this change is at least as new.
            stmt = stmt.on_conflict_do_update(
                index_elements=[Record.user_id, Record.entity, Record.id],
                set_={"updated_at": stmt.excluded.updated_at, "deleted": stmt.excluded.deleted,
                      "data": stmt.excluded.data, "seq": stmt.excluded.seq},
                where=(Record.updated_at <= stmt.excluded.updated_at),
            ).returning(Record.entity, Record.id)
            row = (await db.execute(stmt)).first()
            if row is not None:
                accepted.add((row.entity, row.id))
        await db.commit()

    rows = (
        await db.execute(
            select(Record).where(Record.user_id == user.id, Record.seq > body.cursor).order_by(Record.seq).limit(PULL_LIMIT + 1)
        )
    ).scalars().all()
    has_more = len(rows) > PULL_LIMIT
    page = rows[:PULL_LIMIT] if has_more else rows

    if has_more:
        cursor = page[-1].seq
    else:
        cursor = (await db.execute(select(User.seq).where(User.id == user.id))).scalar_one()

    changes = [
        ChangeOut(entity=r.entity, id=r.id, updatedAt=r.updated_at, deleted=r.deleted, data=r.data)
        for r in page if (r.entity, r.id) not in accepted  # don't echo back what this device just sent
    ]
    return SyncOut(cursor=cursor, hasMore=has_more, changes=changes)
