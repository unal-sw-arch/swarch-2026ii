"""Contrato de eventos: los eventos de ejemplo del Worker cumplen los esquemas compartidos
(docs/equipo/propuestas-yeison/schemas/) y los handlers los aceptan."""
import json
import unittest
from pathlib import Path

import handlers
from tests.fakes import FakeKitchen, FakeOrders, FakeReceipts, FakeStore, paid_event

SCHEMAS = Path(__file__).resolve().parents[2] / "docs" / "equipo" / "propuestas-yeison" / "schemas"
_TYPES = {"string": str, "integer": int, "null": type(None), "array": list, "object": dict}


def check(test, instance, schema, path="$"):
    wanted = schema.get("type")
    if wanted:
        options = wanted if isinstance(wanted, list) else [wanted]
        test.assertTrue(
            any(isinstance(instance, _TYPES[t]) and not (t == "integer" and isinstance(instance, bool)) for t in options),
            f"{path}: se esperaba {wanted}, llegó {type(instance).__name__}",
        )
    if "enum" in schema:
        test.assertIn(instance, schema["enum"], path)
    if isinstance(instance, dict):
        for name in schema.get("required", []):
            test.assertIn(name, instance, f"{path}: falta '{name}'")
        for name, sub in schema.get("properties", {}).items():
            if name in instance:
                check(test, instance[name], sub, f"{path}.{name}")
    if isinstance(instance, list) and "items" in schema:
        for i, element in enumerate(instance):
            check(test, element, schema["items"], f"{path}[{i}]")


def load(name):
    return json.loads((SCHEMAS / f"{name}.schema.json").read_text(encoding="utf-8"))


@unittest.skipUnless(SCHEMAS.exists(), "esquemas compartidos no disponibles en este entorno")
class ContractTests(unittest.TestCase):
    def setUp(self):
        self.deps = handlers.Deps(FakeKitchen(), FakeOrders(), FakeStore(), FakeReceipts(), lambda e: None)

    def test_order_paid(self):
        check(self, paid_event(), load("order.paid"))
        check(self, paid_event(customer_id=None, order_type="en_mesa", table_number=4), load("order.paid"))
        self.assertEqual(handlers.handle_order_paid(paid_event(), self.deps), "processed")

    def test_order_ready(self):
        event = {"event_id": "e", "occurred_at": "2026-11-07T15:10:00Z", "order_id": "o", "ready_at": "2026-11-07T15:10:00Z"}
        check(self, event, load("order.ready"))
        self.assertEqual(handlers.handle_order_ready(event, self.deps), "processed")

    def test_reservas(self):
        base = {"event_id": "e", "occurred_at": "2026-11-07T15:10:00Z", "reservation_id": "r", "customer_id": "c",
                "date": "2026-11-10", "time": "10:00", "people": 2}
        check(self, base, load("reservation.created"))
        check(self, {**base, "reason": "x"}, load("reservation.cancelled"))
        self.assertEqual(handlers.handle_reservation_event("reservation.created", base, self.deps), "processed")


if __name__ == "__main__":
    unittest.main()
