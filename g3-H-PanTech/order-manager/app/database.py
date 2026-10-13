"""Conexión a PostgreSQL (SQLAlchemy) y sesión por request."""
import os

from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://panaderia:panaderia@postgres:5432/panaderia",
)

engine = create_engine(DATABASE_URL, pool_pre_ping=True, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
Base = declarative_base()


def get_db():
    """Dependencia de FastAPI: abre y cierra una sesión por petición."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
