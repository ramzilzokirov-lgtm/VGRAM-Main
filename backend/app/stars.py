from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict
from sqlalchemy import BigInteger, DateTime, ForeignKey, String, select
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, Session, mapped_column

from .main import Base, User, current_user, get_db

router = APIRouter(prefix="/v1/stars", tags=["stars"])


class StarLedger(Base):
    __tablename__ = "star_ledger"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    delta: Mapped[int] = mapped_column(BigInteger, nullable=False)
    reason: Mapped[str] = mapped_column(String(48), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)


class StarBalance(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    balance: int


@router.get("/balance", response_model=StarBalance)
def balance(user: User = Depends(current_user)) -> StarBalance:
    return StarBalance(balance=user.stars)


@router.get("/history")
def history(db: Session = Depends(get_db), user: User = Depends(current_user)) -> list[dict]:
    rows = db.scalars(select(StarLedger).where(StarLedger.user_id == user.id).order_by(StarLedger.created_at.desc()).limit(100)).all()
    return [{"delta": row.delta, "reason": row.reason, "created_at": row.created_at.isoformat()} for row in rows]
