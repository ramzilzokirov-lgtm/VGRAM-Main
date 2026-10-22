from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone
from typing import Generator
from uuid import UUID, uuid4

import jwt
from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, Integer, String, create_engine, select, text
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.environ.get("DATABASE_URL", "")
JWT_SECRET = os.environ.get("JWT_SECRET", "")
if not DATABASE_URL:
    raise RuntimeError("DATABASE_URL is required")
if len(JWT_SECRET) < 32:
    raise RuntimeError("JWT_SECRET must be a random secret of at least 32 characters")
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg://", 1)
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
password_hash = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
TOKEN_MINUTES = 10080
STARTER_STARS = 1000


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    number: Mapped[str] = mapped_column(String(16), unique=True, nullable=False, index=True)
    username: Mapped[str] = mapped_column(String(32), unique=True, nullable=False, index=True)
    display_name: Mapped[str] = mapped_column(String(64), nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    stars: Mapped[int] = mapped_column(Integer, nullable=False, default=STARTER_STARS)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z][a-zA-Z0-9_]{2,31}$")
    password: str = Field(min_length=12, max_length=128)
    display_name: str = Field(min_length=1, max_length=64)


class LoginRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=1, max_length=128)


class UserView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    number: str
    username: str
    display_name: str
    stars: int
    created_at: datetime


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserView


app = FastAPI(title="VGRAM Backend", version="0.1.0", description="Independent VGRAM service. +888 numbers are internal IDs, not phone numbers; this service is not connected to Telegram.")


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def issue_number(db: Session) -> str:
    value = db.execute(text("SELECT nextval('vgram_number_seq')")).scalar_one()
    return f"+888{value:09d}"


def token_for(user: User) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode({"sub": str(user.id), "iss": "vgram-backend", "iat": now, "exp": now + timedelta(minutes=TOKEN_MINUTES), "jti": str(uuid4())}, JWT_SECRET, algorithm="HS256")


def current_user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer), db: Session = Depends(get_db)) -> User:
    if credentials is None:
        raise HTTPException(status_code=401, detail="Bearer token required")
    try:
        payload = jwt.decode(credentials.credentials, JWT_SECRET, algorithms=["HS256"], issuer="vgram-backend")
        user_id = UUID(payload["sub"])
    except (jwt.InvalidTokenError, ValueError, KeyError):
        raise HTTPException(status_code=401, detail="Invalid or expired token") from None
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="Account not found")
    return user


@app.on_event("startup")
def startup() -> None:
    with engine.begin() as conn:
        conn.execute(text("CREATE SEQUENCE IF NOT EXISTS vgram_number_seq START WITH 100000000"))
    Base.metadata.create_all(bind=engine)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "vgram-backend"}


@app.post("/v1/auth/register", response_model=AuthResponse, status_code=201)
def register(body: RegisterRequest, db: Session = Depends(get_db)) -> AuthResponse:
    username = body.username.lower()
    display_name = body.display_name.strip()
    if not display_name:
        raise HTTPException(status_code=422, detail="Display name cannot be blank")
    if db.scalar(select(User.id).where(User.username == username)) is not None:
        raise HTTPException(status_code=409, detail="Username is already taken")
    user = User(number=issue_number(db), username=username, display_name=display_name, password_hash=password_hash.hash(body.password), stars=STARTER_STARS)
    db.add(user)
    try:
        db.commit()
    except Exception as exc:
        db.rollback()
        if "unique" in str(exc).lower() or "duplicate" in str(exc).lower():
            raise HTTPException(status_code=409, detail="Username is already taken") from None
        raise
    db.refresh(user)
    return AuthResponse(access_token=token_for(user), expires_in=TOKEN_MINUTES * 60, user=user)


@app.post("/v1/auth/login", response_model=AuthResponse)
def login(body: LoginRequest, db: Session = Depends(get_db)) -> AuthResponse:
    user = db.scalar(select(User).where(User.username == body.username.lower()))
    if user is None or not password_hash.verify(body.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Incorrect username or password")
    return AuthResponse(access_token=token_for(user), expires_in=TOKEN_MINUTES * 60, user=user)


@app.get("/v1/users/me", response_model=UserView)
def me(user: User = Depends(current_user)) -> User:
    return user


@app.get("/v1/users/by-number/{number}", response_model=UserView)
def lookup_user(number: str, db: Session = Depends(get_db), _user: User = Depends(current_user)) -> User:
    if not number.startswith("+888") or len(number) > 16:
        raise HTTPException(status_code=404, detail="VGRAM user not found")
    user = db.scalar(select(User).where(User.number == number))
    if user is None:
        raise HTTPException(status_code=404, detail="VGRAM user not found")
    return user
