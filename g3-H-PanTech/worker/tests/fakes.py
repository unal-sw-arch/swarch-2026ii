from errors import TransientError


class FakeKitchen:
    def __init__(self):
        self.orders: list[dict] = []
        self.fail_times = 0

    def enqueue(self, order, request_id=None):
        if self.fail_times > 0:
            self.fail_times -= 1
            raise TransientError("Kitchen caída")
        # Kitchen es idempotente por order_id: reencolar el mismo pedido no lo duplica.
        if all(o["order_id"] != order["order_id"] for o in self.orders):
            self.orders.append(order)


class FakeOrders:
    def __init__(self):
        self.calls: list[tuple[str, str]] = []
        self.error: Exception | None = None

    def set_status(self, order_id, status):
        if self.error:
            raise self.error
        self.calls.append((order_id, status))
        return True


class FakeStore:
    def __init__(self):
        self.done: set[tuple[str, str]] = set()
        self.orders: dict[str, dict] = {}
        self.reservations: dict[str, dict] = {}
        self.notifications: dict[tuple[str, str], str] = {}
        self.receipts: dict[str, str] = {}

    def is_done(self, event_id, handler):
        return (event_id, handler) in self.done

    def mark_done(self, event_id, handler):
        self.done.add((event_id, handler))

    def upsert_order_paid(self, order, paid_at):
        self.orders.setdefault(order["order_id"], {"status": "pagado"}).update(
            customer_id=order.get("customer_id"), total=order["total"], paid_at=paid_at
        )

    def mark_order_ready(self, order_id, ready_at):
        self.orders.setdefault(order_id, {}).update(status="listo", ready_at=ready_at)

    def set_receipt(self, order_id, object_name):
        self.receipts[order_id] = object_name

    def upsert_reservation(self, event, *, status, reason=None):
        self.reservations[event["reservation_id"]] = {"status": status, "reason": reason}

    def add_notification(self, event_id, kind, customer_id, message):
        key = (event_id, kind)
        if key in self.notifications:
            return False
        self.notifications[key] = message
        return True


class FakeReceipts:
    def __init__(self):
        self.objects: dict[str, bytes] = {}

    def exists(self, object_name):
        return object_name in self.objects

    def put(self, object_name, data, content_type="application/pdf"):
        self.objects[object_name] = data


def paid_event(**overrides):
    event = {
        "event_id": "ev-1",
        "occurred_at": "2026-11-07T15:00:00+00:00",
        "order_id": "ord-1",
        "customer_id": "cust-1",
        "total": "9000.00",
        "paid_at": "2026-11-07T15:00:00+00:00",
        "order_type": "para_llevar",
        "table_number": None,
        "items": [
            {"product_id": "p-cafe", "name": "Café americano", "quantity": 2, "prep_minutes": 3, "unit_price": "4500.00"}
        ],
    }
    event.update(overrides)
    return event
