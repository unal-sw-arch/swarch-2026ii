from datetime import timedelta

from app.events import outbox
from app.models import utcnow
from tests.perms import CUSTOMER

ORDER = {"order_type": "para_llevar", "items": [{"product_id": "p-pan", "quantity": 1}]}


def _paid_order(client, auth):
    order_id = client.post("/orders", json=ORDER, headers=auth("cust-1", *CUSTOMER)).json()["id"]
    client.post(f"/orders/{order_id}/payment", json={"amount": "3000.00"}, headers=auth("cust-1", *CUSTOMER))
    return order_id


def test_publica_los_eventos_pendientes_una_sola_vez(client, auth, session_factory, publisher, outbox_rows):
    order_id = _paid_order(client, auth)
    assert outbox.publish_pending(session_factory, publisher) == (1, 0)
    assert outbox.publish_pending(session_factory, publisher) == (0, 0)

    routing_key, payload, message_id = publisher.published[0]
    row = outbox_rows()[0]
    assert routing_key == "order.paid"
    assert payload["order_id"] == order_id
    assert payload["event_id"] == message_id == str(row.id)
    assert row.published_at is not None


def test_si_rabbitmq_falla_el_evento_no_se_pierde(client, auth, session_factory, publisher, outbox_rows):
    _paid_order(client, auth)
    publisher.fail = True
    assert outbox.publish_pending(session_factory, publisher) == (0, 1)

    row = outbox_rows()[0]
    assert row.published_at is None
    assert row.attempts == 1
    assert "RabbitMQ" in row.last_error
    assert row.next_attempt_at is not None

    # Aún en backoff: no se reintenta.
    publisher.fail = False
    assert outbox.publish_pending(session_factory, publisher) == (0, 0)

    # Pasado el backoff, se publica y queda marcado.
    with session_factory() as db:
        pending = db.get(type(row), row.id)
        pending.next_attempt_at = utcnow() - timedelta(seconds=1)
        db.commit()
    assert outbox.publish_pending(session_factory, publisher) == (1, 0)
    assert outbox_rows()[0].published_at is not None


def test_conserva_el_orden_de_creacion(client, auth, future_date, session_factory, publisher):
    _paid_order(client, auth)
    client.post(
        "/reservations",
        json={"date": future_date, "time": "10:00", "people": 2},
        headers=auth("cust-1", *CUSTOMER),
    )
    outbox.publish_pending(session_factory, publisher)
    assert [k for k, _, _ in publisher.published] == ["order.paid", "reservation.created"]
