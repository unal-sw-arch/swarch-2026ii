"""Sesiones SQLAlchemy (esquema `orders` de PostgreSQL), MongoDB y Redis."""
from functools import lru_cache

import redis
from pymongo import MongoClient
from pymongo.database import Database
from sqlalchemy import MetaData, create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import settings

SCHEMA = "orders"  # esquema creado por infrastructure/postgres/init.sql (Jaime)

_connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
engine = create_engine(settings.database_url, pool_pre_ping=True, connect_args=_connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    metadata = MetaData(schema=SCHEMA)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@lru_cache
def get_mongo_client() -> MongoClient:
    # MongoClient no abre conexión hasta la primera operación.
    return MongoClient(settings.mongodb_url, tz_aware=True, serverSelectionTimeoutMS=5000)


def get_mongo_db() -> Database:
    return get_mongo_client().get_default_database(default="panaderia")


@lru_cache
def _redis_client() -> redis.Redis:
    return redis.Redis.from_url(
        settings.redis_url, decode_responses=True, socket_connect_timeout=2, socket_timeout=2
    )


def get_redis() -> redis.Redis:
    return _redis_client()
