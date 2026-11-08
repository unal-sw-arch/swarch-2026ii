import os
import time
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal

# Configuración de pruebas ANTES de importar la app.
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
_OTHER_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
_PUBLIC_PEM = _KEY.public_key().public_bytes(
    serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
).decode()

os.environ["DATABASE_URL"] = "sqlite+pysqlite://"
os.environ["JWT_PUBLIC_KEY"] = _PUBLIC_PEM
os.environ["JWT_ISSUER"] = "identity-service"
os.environ["JWT_AUDIENCE"] = "panaderia-express"
os.environ["SERVICE_TOKEN"] = "token-de-pruebas"
os.environ["SEED_MENU"] = "false"
os.environ["OUTBOX_RELAY_ENABLED"] = "false"

import jwt  # noqa: E402
import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine, event, select  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.database import Base, get_db, get_redis  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Outbox  # noqa: E402
from app.repositories.product_repository import Product, get_product_repo  # noqa: E402


class FakeProductRepository:
    """Catálogo en memoria: reemplaza a MongoDB en las pruebas."""

    def __init__(self):
        self.items: dict[str, Product] = {}
        for pid, name, cat, price, prep, active in [
            ("p-cafe", "Café americano", "preparado", "4500", 3, True),
            ("p-pan", "Pan de bono", "vitrina", "3000", 1, True),
            ("p-desayuno", "Desayuno completo", "preparado", "18000", 12, True),
            ("p-retirado", "Pastel retirado", "vitrina", "5000", 1, False),
        ]:
            self.items[pid] = Product(pid, name, cat, Decimal(price), prep, active)

    def list(self, *, active_only=True):
        rows = [p for p in self.items.values() if p.active or not active_only]
        return sorted(rows, key=lambda p: p.name)

    def get(self, product_id):
        return self.items.get(product_id)

    def get_many(self, product_ids, *, active_only=True):
        return {
            pid: self.items[pid]
            for pid in product_ids
            if pid in self.items and (self.items[pid].active or not active_only)
        }

    def create(self, data):
        pid = f"p-new-{len(self.items)}"
        self.items[pid] = Product(pid, data["name"], data["category"], Decimal(str(data["price"])),
                                  data["prep_minutes"], data.get("active", True))
        return self.items[pid]

    def update(self, product_id, patch):
        current = self.items.get(product_id)
        if current is None:
            return None
        fields = {**current.__dict__, **patch}
        fields["price"] = Decimal(str(fields["price"]))
        self.items[product_id] = Product(**fields)
        return self.items[product_id]

    def delete(self, product_id):
        return self.items.pop(product_id, None) is not None


class FakeRedis:
    """Subconjunto de redis.Redis que usa el Order Service."""

    def __init__(self):
        self.store: dict[str, str] = {}

    def get(self, key):
        return self.store.get(key)

    def set(self, key, value, nx=False, ex=None):
        if nx and key in self.store:
            return None
        self.store[key] = value
        return True

    def delete(self, key):
        self.store.pop(key, None)


class FakePublisher:
    def __init__(self):
        self.published: list[tuple[str, dict, str]] = []
        self.fail = False

    def publish(self, routing_key, payload, *, message_id):
        if self.fail:
            raise ConnectionError("RabbitMQ no disponible")
        self.published.append((routing_key, payload, message_id))


@pytest.fixture()
def db_engine():
    engine = create_engine(
        "sqlite+pysqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )

    @event.listens_for(engine, "connect")
    def _attach_schema(dbapi_connection, _):  # el esquema `orders` de PostgreSQL → base adjunta en SQLite
        dbapi_connection.execute("ATTACH DATABASE ':memory:' AS orders")

    Base.metadata.create_all(engine)
    yield engine
    engine.dispose()


@pytest.fixture()
def session_factory(db_engine):
    return sessionmaker(bind=db_engine, autoflush=False, expire_on_commit=False)


@pytest.fixture()
def fake_redis():
    return FakeRedis()


@pytest.fixture()
def products():
    return FakeProductRepository()


@pytest.fixture()
def client(session_factory, fake_redis, products):
    def override_get_db():
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_redis] = lambda: fake_redis
    app.dependency_overrides[get_product_repo] = lambda: products
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture()
def make_token():
    def _make(sub, *permissions, roles=(), level=10, expires_in=3600, wrong_key=False, issuer="identity-service", audience="panaderia-express"):
        now = int(time.time())
        payload = {
            "sub": sub,
            "email": f"{sub}@example.com",
            "roles": list(roles),
            "permissions": list(permissions),
            "level": level,
            "iss": issuer,
            "aud": audience,
            "iat": now,
            "exp": now + expires_in,
        }
        return jwt.encode(payload, _OTHER_KEY if wrong_key else _KEY, algorithm="RS256")

    return _make


@pytest.fixture()
def auth(make_token):
    def _headers(sub, *permissions, **kwargs):
        return {"Authorization": f"Bearer {make_token(sub, *permissions, **kwargs)}"}

    return _headers


@pytest.fixture()
def outbox_rows(session_factory):
    def _rows(event_type=None):
        with session_factory() as db:
            stmt = select(Outbox).order_by(Outbox.created_at)
            if event_type:
                stmt = stmt.where(Outbox.event_type == event_type)
            return list(db.scalars(stmt))

    return _rows


@pytest.fixture()
def publisher():
    return FakePublisher()


@pytest.fixture()
def future_date():
    return (datetime.now(timezone.utc) + timedelta(days=3)).date().isoformat()
