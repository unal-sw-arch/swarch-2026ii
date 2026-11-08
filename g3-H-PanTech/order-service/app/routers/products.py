from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.repositories.product_repository import ProductRepository, get_product_repo
from app.schemas import ProductIn, ProductOut, ProductUpdate
from app.security import Principal, get_current_principal, require_permission
from app.services import menu_service

router = APIRouter(prefix="/products", tags=["products"])


@router.get("", response_model=list[ProductOut], summary="Catálogo (los inactivos solo los ve products:manage)")
def list_products(
    principal: Principal = Depends(get_current_principal),
    repo: ProductRepository = Depends(get_product_repo),
):
    return menu_service.list_products(repo, include_inactive=principal.has("products:manage"))


@router.get("/{product_id}", response_model=ProductOut, summary="Detalle de un producto")
def get_product(
    product_id: str,
    principal: Principal = Depends(get_current_principal),
    repo: ProductRepository = Depends(get_product_repo),
):
    return menu_service.get_product(repo, product_id, include_inactive=principal.has("products:manage"))


@router.post("", response_model=ProductOut, status_code=status.HTTP_201_CREATED, summary="Crear producto")
def create_product(
    payload: ProductIn,
    _: Principal = Depends(require_permission("products:manage")),
    repo: ProductRepository = Depends(get_product_repo),
):
    return menu_service.create_product(repo, payload.model_dump())


@router.put("/{product_id}", response_model=ProductOut, summary="Actualizar producto")
def update_product(
    product_id: str,
    payload: ProductUpdate,
    _: Principal = Depends(require_permission("products:manage")),
    repo: ProductRepository = Depends(get_product_repo),
):
    return menu_service.update_product(repo, product_id, payload.model_dump(exclude_unset=True))


@router.delete(
    "/{product_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Borrar producto (409 si ya tiene pedidos)",
)
def delete_product(
    product_id: str,
    _: Principal = Depends(require_permission("products:manage")),
    db: Session = Depends(get_db),
    repo: ProductRepository = Depends(get_product_repo),
):
    menu_service.delete_product(db, repo, product_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
