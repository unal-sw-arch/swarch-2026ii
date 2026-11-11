import unittest

import handlers
from errors import InvalidEvent
from tests.fakes import FakeKitchen, FakeOrders, FakeReceipts, FakeStore

BASE = {"reservation_id": "r-1", "customer_id": "cust-1", "date": "2026-11-10", "time": "10:00", "people": 2}


class ReservationEventTests(unittest.TestCase):
    def setUp(self):
        self.store = FakeStore()
        self.deps = handlers.Deps(FakeKitchen(), FakeOrders(), self.store, FakeReceipts(), lambda e: None)

    def test_created_confirmed_cancelled_actualizan_el_read_model(self):
        handlers.handle_reservation_event("reservation.created", {**BASE, "event_id": "e1"}, self.deps)
        self.assertEqual(self.store.reservations["r-1"]["status"], "pendiente")
        handlers.handle_reservation_event("reservation.confirmed", {**BASE, "event_id": "e2"}, self.deps)
        self.assertEqual(self.store.reservations["r-1"]["status"], "confirmada")
        handlers.handle_reservation_event(
            "reservation.cancelled", {**BASE, "event_id": "e3", "reason": "Cambio de planes"}, self.deps
        )
        self.assertEqual(self.store.reservations["r-1"], {"status": "cancelada", "reason": "Cambio de planes"})
        self.assertEqual(len(self.store.notifications), 3)

    def test_evento_repetido_no_duplica_notificacion(self):
        event = {**BASE, "event_id": "e1"}
        handlers.handle_reservation_event("reservation.created", event, self.deps)
        self.assertEqual(handlers.handle_reservation_event("reservation.created", event, self.deps), "duplicate")
        self.assertEqual(len(self.store.notifications), 1)

    def test_evento_sin_reservation_id_es_invalido(self):
        with self.assertRaises(InvalidEvent):
            handlers.handle_reservation_event("reservation.created", {"event_id": "e1"}, self.deps)


if __name__ == "__main__":
    unittest.main()
