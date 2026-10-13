import os

from sqlalchemy import URL, create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker


def _database_url() -> URL:
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = os.getenv("POSTGRES_PORT", "5433")
    database = os.getenv("POSTGRES_DB", "metadataba_db")
    user = os.getenv("POSTGRES_USER")
    password = os.getenv("POSTGRES_PASSWORD")

    return URL.create(
        "postgresql+psycopg",
        username=user,
        password=password,
        host=host,
        port=int(port),
        database=database,
    )


engine = create_engine(
    _database_url(), pool_pre_ping=True, connect_args={"connect_timeout": 5}
)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass
