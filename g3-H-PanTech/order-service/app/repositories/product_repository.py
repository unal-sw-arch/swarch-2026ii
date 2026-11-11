"""Catálogo de productos en MongoDB (colección `menu`): fuente de verdad del menú.

Documento:
    {"product_id": "p-cafe-americano", "name": "Café americano", "category": "vitrina",
     "price": Decimal128("3.50"), "prep_minutes": 1, "available": true}
"""
import uuid
from dataclasses import dataclass
from decimal import Decimal
from typing import Iterable, Protocol

from bson.decimal128 import Decimal128
from pymongo.collection import Collection
from pymongo.database import Database
from pymongo.errors import DuplicateKeyError

from app.database import get_mongo_db


@dataclass(frozen=True)
class Product:
    product_id: str
    name: str
    category: str  # "vitrina" | "preparado"
    price: Decimal
    prep_minutes: int
    active: bool = True


class ProductRepository(Protocol):
    def list(self, *, active_only: bool = True) -> list[Product]: ...

    def get(self, product_id: str) -> Product | None:
        """Devuelve el producto aunque esté inactivo."""
        ...

    def get_many(self, product_ids: Iterable[str], *, active_only: bool = True) -> dict[str, Product]: ...

    def create(self, data: dict) -> Product: ...

    def update(self, product_id: str, patch: dict) -> Product | None: ...

    def delete(self, product_id: str) -> bool: ...


def _to_decimal(value) -> Decimal:
    if isinstance(value, Decimal128):
        return value.to_decimal()
    return Decimal(str(value))


def _to_product(doc: dict) -> Product:
    return Product(
        product_id=doc["product_id"],
        name=doc["name"],
        category=doc["category"],
        price=_to_decimal(doc["price"]),
        prep_minutes=int(doc["prep_minutes"]),
        active=bool(doc.get("available", doc.get("active", False))),
    )


def _to_doc(data: dict) -> dict:
    doc = dict(data)
    if "active" in doc:  # la API dice `active`; el documento de Mongo dice `available`
        doc["available"] = doc.pop("active")
    if "price" in doc and doc["price"] is not None:
        doc["price"] = Decimal128(str(doc["price"]))
    return doc


class MongoProductRepository:
    def __init__(self, db: Database):
        self._col: Collection = db["menu"]

    def list(self, *, active_only: bool = True) -> list[Product]:
        query = {"available": True} if active_only else {}
        return [_to_product(d) for d in self._col.find(query, {"_id": 0}).sort("name", 1)]

    def get(self, product_id: str) -> Product | None:
        doc = self._col.find_one({"product_id": product_id}, {"_id": 0})
        return _to_product(doc) if doc else None

    def get_many(self, product_ids: Iterable[str], *, active_only: bool = True) -> dict[str, Product]:
        query: dict = {"product_id": {"$in": list(product_ids)}}
        if active_only:
            query["available"] = True
        return {d["product_id"]: _to_product(d) for d in self._col.find(query, {"_id": 0})}

    def create(self, data: dict) -> Product:
        doc = _to_doc({"product_id": uuid.uuid4().hex, **data})
        try:
            self._col.insert_one(dict(doc))
        except DuplicateKeyError:  # colisión de uuid: prácticamente imposible
            doc["product_id"] = uuid.uuid4().hex
            self._col.insert_one(dict(doc))
        return _to_product(doc)

    def update(self, product_id: str, patch: dict) -> Product | None:
        from pymongo import ReturnDocument

        doc = self._col.find_one_and_update(
            {"product_id": product_id},
            {"$set": _to_doc(patch)},
            projection={"_id": 0},
            return_document=ReturnDocument.AFTER,
        )
        return _to_product(doc) if doc else None

    def delete(self, product_id: str) -> bool:
        return self._col.delete_one({"product_id": product_id}).deleted_count > 0


def get_product_repo() -> ProductRepository:
    return MongoProductRepository(get_mongo_db())
