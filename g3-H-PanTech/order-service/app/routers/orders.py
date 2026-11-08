import uuid

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.repositories.product_repository import ProductRepository, get_product_repo
from app.schemas import OrderCreate, OrderOut, OrderStatusOut
from app.security import Principal, require_permission
from app.services import order_service

router = APIRouter(prefix="/orders", tags=["orders"])

_READ = ("orders:read_all", "orders:read_own")


@router.post("", response_model=OrderOut, status_code=status.HTTP_201_CREATED, summary="Crear pedido")
def create_order(
    payload: OrderCreate,
    principal: Principal = Depends(require_permission("orders:create", "orders:create_own")),
    db: Session = Depends(get_db),
    products: ProductRepository = Depends(get_product_repo),
):
    return order_service.create_order(db, products, principal, payload)


@router.get("", response_model=list[OrderOut], summary="Listar pedidos (propios o todos según permiso)")
def list_orders(
    status_filter: str | None = Query(None, alias="status"),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    principal: Principal = Depends(require_permission(*_READ)),
    db: Session = Depends(get_db),
):
    return order_service.list_visible_orders(
        db, principal, status=status_filter, limit=limit, offset=offset
    )


@router.get("/{order_id}", response_model=OrderOut, summary="Detalle de un pedido")
def get_order(
    order_id: uuid.UUID,
    principal: Principal = Depends(require_permission(*_READ)),
    db: Session = Depends(get_db),
):
    return order_service.get_visible_order(db, order_id, principal)


@router.get("/{order_id}/status", response_model=OrderStatusOut, summary="Estado del pedido (polling)")
def get_order_status(
    order_id: uuid.UUID,
    principal: Principal = Depends(require_permission(*_READ)),
    db: Session = Depends(get_db),
):
    return order_service.get_visible_order(db, order_id, principal)
