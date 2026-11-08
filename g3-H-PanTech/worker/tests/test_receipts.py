import unittest

import handlers
from errors import InvalidEvent
from tests.fakes import FakeKitchen, FakeOrders, FakeReceipts, FakeStore, paid_event


class ReceiptTests(unittest.TestCase):
    def setUp(self):
        self.receipts, self.store = FakeReceipts(), FakeStore()
        self.deps = handlers.Deps(FakeKitchen(), FakeOrders(), self.store, self.receipts, lambda e: None)

    def test_genera_un_pdf_por_pedido_pagado(self):
        self.assertEqual(handlers.generate_receipt(paid_event(), self.deps), "created")
        pdf = self.receipts.objects["ord-1.pdf"]
        self.assertTrue(pdf.startswith(b"%PDF"))
        self.assertEqual(self.store.receipts["ord-1"], "ord-1.pdf")

    def test_no_se_vuelve_a_generar_si_ya_existe(self):
        handlers.generate_receipt(paid_event(), self.deps)
        first = self.receipts.objects["ord-1.pdf"]
        self.assertEqual(handlers.generate_receipt(paid_event(event_id="otro"), self.deps), "exists")
        self.assertIs(self.receipts.objects["ord-1.pdf"], first)

    def test_pedido_en_mesa_y_evento_incompleto(self):
        event = paid_event(order_id="ord-2", order_type="en_mesa", table_number=4)
        self.assertEqual(handlers.generate_receipt(event, self.deps), "created")
        with self.assertRaises(InvalidEvent):
            handlers.generate_receipt({"order_id": "x"}, self.deps)


if __name__ == "__main__":
    unittest.main()
