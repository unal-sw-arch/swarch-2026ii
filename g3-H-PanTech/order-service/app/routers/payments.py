import uuid

from fastapi import APIRouter, Depends, Header, Request, Response, status
from sqlalchemy.orm import Session

from app.database import get_db, get_redis
from app.events import outbox
from app.schemas import PaymentCreate, PaymentOut
from app.security import Principal, require_permission
from app.services import payment_service

router = APIRouter(tags=["payments"])


@router.post(
    "/orders/{order_id}/payment",
    response_model=PaymentOut,
    status_code=status.HTTP_201_CREATED,
    summary="Pagar un pedido (simulado, idempotente)",
    description=(
        "`amount` debe ser igual al total calculado por el servidor. Envía `Idempotency-Key` para poder "
        "reintentar sin riesgo: una repetición con la misma llave responde 200 con el pago original y "
        "no publica otro `order.paid`."
    ),
)
def pay_order(
    order_id: uuid.UUID,
    payload: PaymentCreate,
    request: Request,
    response: Response,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key", max_length=100),
    principal: Principal = Depends(require_permission("orders:charge", "orders:create_own")),
    db: Session = Depends(get_db),
    redis_client=Depends(get_redis),
):
    payment, order, replay = payment_service.pay_order(
        db, redis_client, principal, order_id, payload.amount, idempotency_key
    )
    if replay:
        response.status_code = status.HTTP_200_OK
    else:
        outbox.wake(request.app)
    return PaymentOut(
        payment_id=payment.id,
        order_id=order.id,
        amount=payment.amount,
        method=payment.method,
        status=payment.status,
        paid_by=payment.paid_by,
        paid_at=payment.created_at,
        order_status=order.status,
        idempotent_replay=replay,
    )
