"""Rutas de pedidos: persistencia en PostgreSQL y encolado en el Queue Scheduler."""
import logging

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Order, OrderItem, Product
from ..schemas import OrderCreate, OrderOut
from ..scheduler_client import SchedulerError, enqueue_order

logger = logging.getLogger("order-manager.orders")

router = APIRouter(tags=["pedidos"])


@router.get("/orders", response_model=list[OrderOut])
def list_orders(db: Session = Depends(get_db)):
    return db.query(Order).order_by(Order.id.desc()).limit(50).all()


@router.get("/orders/{order_id}", response_model=OrderOut)
def get_order(order_id: int, db: Session = Depends(get_db)):
    order = db.get(Order, order_id)
    if order is None:
        raise HTTPException(status_code=404, detail="Pedido no encontrado")
    return order


@router.post("/orders", response_model=OrderOut, status_code=201)
async def create_order(payload: OrderCreate, db: Session = Depends(get_db)):
    """Crea el pedido en PostgreSQL y lo envía a cocina.

    Si el Queue Scheduler falla, se revierte la creación para no dejar el pedido
    a medias y se responde con un error claro.
    """
    if payload.type == "en_mesa" and payload.table_number is None:
        raise HTTPException(
            status_code=400, detail="Un pedido 'en_mesa' requiere table_number"
        )

    # Validar productos existentes y activos.
    product_ids = [item.product_id for item in payload.items]
    products = {
        p.id: p for p in db.query(Product).filter(Product.id.in_(product_ids)).all()
    }
    missing = [pid for pid in product_ids if pid not in products]
    if missing:
        raise HTTPException(status_code=400, detail=f"Productos inexistentes: {missing}")

    order = Order(type=payload.type, table_number=payload.table_number)
    for item in payload.items:
        order.items.append(OrderItem(product_id=item.product_id, quantity=item.quantity))
    db.add(order)
    db.commit()
    db.refresh(order)

    try:
        await enqueue_order(
            order_id=order.id,
            order_type=order.type,
            table_number=order.table_number,
            items=[
                {"product_id": item.product_id, "quantity": item.quantity}
                for item in order.items
            ],
        )
    except SchedulerError as exc:
        # Revertir: el pedido no quedó en cocina, así que no debe persistir.
        logger.error("Fallo al encolar el pedido %s: %s", order.id, exc)
        db.delete(order)
        db.commit()
        raise HTTPException(status_code=502, detail=str(exc))

    return order
