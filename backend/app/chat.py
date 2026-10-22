from __future__ import annotations

from datetime import datetime, timezone
from typing import Generator
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import DateTime, ForeignKey, String, Text, and_, or_, select
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, Session, mapped_column

from .main import Base, User, current_user, get_db

router = APIRouter(prefix="/v1", tags=["messaging"])


class Conversation(Base):
    __tablename__ = "conversations"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_low: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    user_high: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)


class Message(Base):
    __tablename__ = "messages"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    conversation_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False, index=True)
    sender_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)


class StartChatRequest(BaseModel):
    number: str = Field(min_length=13, max_length=16, pattern=r"^\+888[0-9]{9,12}$")


class SendMessageRequest(BaseModel):
    body: str = Field(min_length=1, max_length=4000)


class MessageView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    conversation_id: UUID
    sender_id: UUID
    body: str
    created_at: datetime


def chat_for_user(db: Session, conversation_id: UUID, user_id: UUID) -> Conversation:
    chat = db.get(Conversation, conversation_id)
    if chat is None or user_id not in (chat.user_low, chat.user_high):
        raise HTTPException(status_code=404, detail="Conversation not found")
    return chat


@router.post("/chats", status_code=201)
def start_chat(body: StartChatRequest, db: Session = Depends(get_db), me: User = Depends(current_user)) -> dict:
    peer = db.scalar(select(User).where(User.number == body.number))
    if peer is None:
        raise HTTPException(status_code=404, detail="VGRAM user not found")
    if peer.id == me.id:
        raise HTTPException(status_code=400, detail="Cannot start a chat with yourself")
    low, high = sorted((me.id, peer.id), key=str)
    existing = db.scalar(select(Conversation).where(and_(Conversation.user_low == low, Conversation.user_high == high)))
    if existing:
        return {"id": str(existing.id), "peer": {"number": peer.number, "username": peer.username, "display_name": peer.display_name}}
    chat = Conversation(user_low=low, user_high=high)
    db.add(chat)
    try:
        db.commit()
    except Exception:
        db.rollback()
        existing = db.scalar(select(Conversation).where(and_(Conversation.user_low == low, Conversation.user_high == high)))
        if existing:
            return {"id": str(existing.id), "peer": {"number": peer.number, "username": peer.username, "display_name": peer.display_name}}
        raise
    db.refresh(chat)
    return {"id": str(chat.id), "peer": {"number": peer.number, "username": peer.username, "display_name": peer.display_name}}


@router.get("/chats")
def list_chats(db: Session = Depends(get_db), me: User = Depends(current_user)) -> list[dict]:
    chats = db.scalars(select(Conversation).where(or_(Conversation.user_low == me.id, Conversation.user_high == me.id)).order_by(Conversation.created_at.desc())).all()
    result = []
    for chat in chats:
        peer_id = chat.user_high if chat.user_low == me.id else chat.user_low
        peer = db.get(User, peer_id)
        if peer is not None:
            result.append({"id": str(chat.id), "created_at": chat.created_at.isoformat(), "peer": {"number": peer.number, "username": peer.username, "display_name": peer.display_name}})
    return result


@router.get("/chats/{conversation_id}/messages", response_model=list[MessageView])
def list_messages(conversation_id: UUID, before: datetime | None = Query(default=None), limit: int = Query(default=50, ge=1, le=100), db: Session = Depends(get_db), me: User = Depends(current_user)) -> list[Message]:
    chat_for_user(db, conversation_id, me.id)
    stmt = select(Message).where(Message.conversation_id == conversation_id)
    if before is not None:
        stmt = stmt.where(Message.created_at < before)
    return list(reversed(db.scalars(stmt.order_by(Message.created_at.desc()).limit(limit)).all()))


@router.post("/chats/{conversation_id}/messages", response_model=MessageView, status_code=201)
def send_message(conversation_id: UUID, body: SendMessageRequest, db: Session = Depends(get_db), me: User = Depends(current_user)) -> Message:
    chat_for_user(db, conversation_id, me.id)
    clean_body = body.body.strip()
    if not clean_body:
        raise HTTPException(status_code=422, detail="Message cannot be blank")
    message = Message(conversation_id=conversation_id, sender_id=me.id, body=clean_body)
    db.add(message)
    db.commit()
    db.refresh(message)
    return message
