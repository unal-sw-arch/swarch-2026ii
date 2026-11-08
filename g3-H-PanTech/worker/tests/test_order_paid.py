import unittest

import handlers
from errors import InvalidEvent, TransientError
from tests.fakes import FakeKitchen, FakeOrders, FakeReceipts, FakeStore, paid_event


class OrderPaidTests(unittest.TestCase):
    def setUp(self):
        self.kitchen, self.store = FakeKitchen(), FakeStore()
        self.dispatched: list[dict] = []
        self.deps = handlers.Deps(
            kitchen=self.kitchen, orders=FakeOrders(), store=self.store, receipts=FakeReceipts(),
            dispatch_receipt=self.dispatched.append,
        )

    def test_encola_en_cocina_actualiza_read_model_notifica_y_pide_comprobante(self):
        self.assertEqual(handlers.handle_order_paid(paid_event(), self.deps), "processed")
        self.assertEqual(len(self.kitchen.orders), 1)
        sent = self.kitchen.orders[0]
        self.assertEqual((sent["order_id"], sent["type"], sent["customer_id"]), ("ord-1", "para_llevar", "cust-1"))
        self.assertEqual(sent["items"][0]["prep_minutes"], 3)
        self.assertEqual(self.store.orders["ord-1"]["status"], "pagado")
        self.assertIn(("ev-1", "order_paid"), self.store.notifications)
        self.assertEqual(len(self.dispatched), 1)
        self.assertIn(("ev-1", "order.paid"), self.store.done)

    def test_evento_repetido_no_duplica_efectos(self):
        handlers.handle_order_paid(paid_event(), self.deps)
        self.assertEqual(handlers.handle_order_paid(paid_event(), self.deps), "duplicate")
        self.assertEqual(len(self.kitchen.orders), 1)
        self.assertEqual(len(self.dispatched), 1)
        self.assertEqual(len(self.store.notifications), 1)

    def test_fallo_de_kitchen_se_reintenta_sin_marcarlo_como_hecho(self):
        self.kitchen.fail_times = 2
        for _ in range(2):
            with self.assertRaises(TransientError):
                handlers.handle_order_paid(paid_event(), self.deps)
        self.assertNotIn(("ev-1", "order.paid"), self.store.done)
        self.assertEqual(self.dispatched, [])  # sin cocina no hay comprobante ni notificación

        self.assertEqual(handlers.handle_order_paid(paid_event(), self.deps), "processed")
        self.assertEqual(len(self.kitchen.orders), 1)

    def test_el_mismo_pedido_con_otro_event_id_no_duplica_la_comanda(self):
        handlers.handle_order_paid(paid_event(), self.deps)
        handlers.handle_order_paid(paid_event(event_id="ev-2"), self.deps)  # reentrega con otro id
        self.assertEqual(len(self.kitchen.orders), 1)  # Kitchen es idempotente por order_id

    def test_eventos_invalidos_no_se_reintentan(self):
        for bad in (paid_event(order_id=None), paid_event(items=[]), paid_event(paid_at="ayer"), "no-es-dict"):
            with self.assertRaises(InvalidEvent):
                handlers.handle_order_paid(bad, self.deps)
        self.assertEqual(self.kitchen.orders, [])


if __name__ == "__main__":
    unittest.main()
