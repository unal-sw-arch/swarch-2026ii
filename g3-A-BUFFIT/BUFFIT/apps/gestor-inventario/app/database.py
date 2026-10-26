from pymongo import MongoClient, ASCENDING
from app.config import Config

_client = None


def get_db(client=None):
    """Devuelve la BD de inventario (MongoDB). Acepta un cliente inyectado (tests)."""
    global _client
    if client is not None:
        _client = client
    if _client is None:
        _client = MongoClient(Config.MONGO_URI, serverSelectionTimeoutMS=5000)
    return _client[Config.MONGO_DB]


def init_indexes(db):
    db.items.create_index(
        [("dining_hall", ASCENDING), ("product_name", ASCENDING)], unique=True
    )
