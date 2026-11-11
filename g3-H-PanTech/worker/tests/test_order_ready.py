import unittest

import handlers
from errors import InvalidEvent, TransientError
from tests.fakes import FakeKitchen, FakeOrders, FakeReceipts, FakeStore

READY = {"event_id": "ev-9", "order_id": "ord-1", "ready_at": "2026-11-07T15:10:00Z", "occurred_at": "2026-11-07T15:10:00Z"}


class OrderReadyTests(unittest.TestCase):
    def setUp(self):
        self.orders, self.store = FakeOrders(), FakeStore()
        self.deps = handlers.Deps(FakeKitchen(), self.orders, self.store, FakeReceipts(), lambda e: None)

    def test_marca_listo_en_order_service_y_en_el_read_model(self):
        self.assertEqual(handlers.handle_order_ready(READY, self.deps), "processed")
        self.assertEqual(self.orders.calls, [("ord-1", "listo")])
        self.assertEqual(self.store.orders["ord-1"]["status"], "listo")
        self.assertIn(("ev-9", "order_ready"), self.store.notifications)

    def test_evento_repetido_se_ignora(self):
        handlers.handle_order_ready(READY, self.deps)
        self.assertEqual(handlers.handle_order_ready(READY, self.deps), "duplicate")
        self.assertEqual(len(self.orders.calls), 1)

    def test_errores_del_order_service_se_propagan_para_reintentar_o_ir_a_la_dlq(self):
        self.orders.error = TransientError("409 aún sin pago")
        with self.assertRaises(TransientError):
            handlers.handle_order_ready(READY, self.deps)
        self.orders.error = InvalidEvent("el pedido no existe")
        with self.assertRaises(InvalidEvent):
            handlers.handle_order_ready(READY, self.deps)
        self.assertNotIn(("ev-9", "order.ready"), self.store.done)

    def test_evento_sin_ready_at_es_invalido(self):
        with self.assertRaises(InvalidEvent):
            handlers.handle_order_ready({"event_id": "x", "order_id": "o"}, self.deps)

    def test_acepta_el_sobre_canonico_de_kitchen(self):
        envelope = {
            "event_id": "ev-envelope",
            "event_type": "order.ready",
            "version": 1,
            "occurred_at": "2026-11-07T15:10:00Z",
            "producer": "kitchen-service",
            "payload": {
                "order_id": "ord-9",
                "customer_id": "cust-9",
                "ready_at": "2026-11-07T15:10:00Z",
                "prep_minutes": 5,
            },
        }
        self.assertEqual(handlers.handle_order_ready(envelope, self.deps), "processed")
        self.assertEqual(self.orders.calls, [("ord-9", "listo")])
        self.assertIn(("ev-envelope", "order_ready"), self.store.notifications)


if __name__ == "__main__":
    unittest.main()
