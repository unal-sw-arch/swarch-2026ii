import importlib.util
import unittest

import config
from dispatch import dispatch_domain_message


class DispatchTests(unittest.TestCase):
    def setUp(self):
        self.sent, self.dead = [], []

    def _dispatch(self, name, body):
        dispatch_domain_message(
            name, body,
            send_task=lambda task, args: self.sent.append((task, args)),
            dead_letter=lambda n, b, reason: self.dead.append((n, b, reason)),
        )

    def test_cada_evento_va_a_su_tarea(self):
        for name, task in config.EVENT_TASKS.items():
            self._dispatch(name, {"event_id": "e"})
        self.assertEqual([t for t, _ in self.sent], list(config.EVENT_TASKS.values()))
        self.assertEqual(self.dead, [])

    def test_evento_desconocido_o_mal_formado_va_a_la_dlq(self):
        self._dispatch("order.cancelled", {"event_id": "e"})
        self._dispatch("order.paid", "texto")
        self._dispatch("order.paid", {"sin": "event_id"})
        self.assertEqual(self.sent, [])
        self.assertEqual(len(self.dead), 3)


@unittest.skipUnless(importlib.util.find_spec("celery"), "requiere celery instalado")
class RetryConfigTests(unittest.TestCase):
    """Reintentos con backoff y DLQ al agotarlos (Y4.4)."""

    def test_todas_las_tareas_reintentan_y_terminan_en_la_dlq(self):
        from errors import InvalidEvent
        from tasks.order_paid import process_order_paid
        from tasks.order_ready import process_order_ready
        from tasks.receipts import generate_receipt_task
        from tasks.reservation_events import process_reservation_cancelled
        from tasks.base import EventTask

        for task in (process_order_paid, process_order_ready, generate_receipt_task, process_reservation_cancelled):
            self.assertIsInstance(task, EventTask)
            self.assertEqual(task.max_retries, config.MAX_RETRIES)
            self.assertIn(Exception, task.autoretry_for)
            self.assertIn(InvalidEvent, task.dont_autoretry_for)
            self.assertTrue(task.retry_backoff)

    def test_on_failure_publica_en_la_dlq(self):
        from unittest import mock

        from tasks.base import EventTask
        from tasks.order_paid import process_order_paid

        with mock.patch("tasks.base.publish_to_dlq") as publish:
            EventTask.on_failure(process_order_paid, RuntimeError("boom"), "id-1", [{"event_id": "e"}], {}, None)
        publish.assert_called_once()
        self.assertEqual(publish.call_args.args[0], "order.paid")
        self.assertEqual(publish.call_args.args[1], {"event_id": "e"})


@unittest.skipUnless(importlib.util.find_spec("httpx"), "requiere httpx instalado")
class ClientTests(unittest.TestCase):
    def _kitchen(self, status):
        import httpx

        from clients.kitchen_client import KitchenClient

        return KitchenClient("http://kitchen", "tok", transport=httpx.MockTransport(lambda r: httpx.Response(status, json={})))

    def test_kitchen_acepta_201_y_409_pero_reintenta_5xx_y_descarta_422(self):
        from errors import InvalidEvent, TransientError

        self._kitchen(201).enqueue({"order_id": "o"})
        self._kitchen(409).enqueue({"order_id": "o"})  # ya estaba encolado
        with self.assertRaises(TransientError):
            self._kitchen(503).enqueue({"order_id": "o"})
        with self.assertRaises(InvalidEvent):
            self._kitchen(422).enqueue({"order_id": "o"})

    def test_kitchen_envia_service_token(self):
        import httpx

        from clients.kitchen_client import KitchenClient

        seen = {}

        def handler(request):
            seen["token"] = request.headers.get("X-Service-Token")
            seen["path"] = request.url.path
            return httpx.Response(201, json={})

        KitchenClient("http://kitchen", "tok", transport=httpx.MockTransport(handler)).enqueue({"order_id": "o"})
        self.assertEqual(seen, {"token": "tok", "path": "/queue/orders"})


if __name__ == "__main__":
    unittest.main()


class TopologyTests(unittest.TestCase):
    """El Worker consume exactamente las colas de infrastructure/rabbitmq/definitions.json."""

    def test_colas_por_defecto_son_las_de_definitions_json(self):
        self.assertEqual(
            config.EVENTS_QUEUES,
            ["worker.order_paid", "worker.order_ready", "worker.reservation_created", "worker.reservation_cancelled"],
        )

    def test_cada_cola_se_enlaza_con_un_evento_que_tiene_tarea(self):
        for queue in config.EVENTS_QUEUES:
            self.assertIn(config.QUEUE_BINDINGS[queue], config.EVENT_TASKS)

    def test_dead_letter_usa_la_dlx_de_jaime(self):
        self.assertEqual((config.DLX_EXCHANGE, config.DLQ_QUEUE), ("panaderia.dlx", "panaderia.dlq"))

    def test_el_endpoint_s3_se_separa_en_host_y_esquema(self):
        self.assertEqual(config.S3_ENDPOINT_HOST, "rustfs:9000")
        self.assertFalse(config.S3_SECURE)
