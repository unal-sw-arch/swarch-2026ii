"""Catálogo y menú (MongoDB). El menú es la vista pública de los productos activos."""
from sqlalchemy.orm import Session

from app.errors import Conflict, NotFound
from app.repositories import order_repository
from app.repositories.product_repository import Product, ProductRepository


def list_menu(repo: ProductRepository) -> list[Product]:
    return repo.list(active_only=True)


def list_products(repo: ProductRepository, *, include_inactive: bool) -> list[Product]:
    return repo.list(active_only=not include_inactive)


def get_product(repo: ProductRepository, product_id: str, *, include_inactive: bool) -> Product:
    product = repo.get(product_id)
    if product is None or (not product.active and not include_inactive):
        raise NotFound("Producto no encontrado")
    return product


def create_product(repo: ProductRepository, data: dict) -> Product:
    return repo.create(data)


def update_product(repo: ProductRepository, product_id: str, patch: dict) -> Product:
    if not patch:
        return get_product(repo, product_id, include_inactive=True)
    product = repo.update(product_id, patch)
    if product is None:
        raise NotFound("Producto no encontrado")
    return product


def delete_product(db: Session, repo: ProductRepository, product_id: str) -> None:
    """Un producto con pedidos asociados no se borra (409): se desactiva con PUT."""
    if repo.get(product_id) is None:
        raise NotFound("Producto no encontrado")
    if order_repository.product_has_orders(db, product_id):
        raise Conflict(
            "El producto tiene pedidos asociados y no se puede borrar; desactívalo con PUT (active=false)"
        )
    repo.delete(product_id)
