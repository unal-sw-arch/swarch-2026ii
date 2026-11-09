"""Kitchen Service FALSO para probar el Worker (el real lo hace Axel). Escucha en :8001.

    python fake_kitchen.py              # acepta pedidos (201) y los imprime
    FAIL=1 python fake_kitchen.py       # responde 503: sirve para ver reintentos y la DLQ
"""
import json
import os
import threading
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer

FAIL = os.getenv("FAIL") == "1"
READY_AFTER = float(os.getenv("READY_AFTER_SECONDS", "10"))
RABBITMQ_URL = os.getenv("RABBITMQ_URL")  # si se define, publica `order.ready` solo, como haría Kitchen
SEEN: set[str] = set()


def publish_ready(order_id: str) -> None:
    try:
        import pika

        now = datetime.now(timezone.utc).isoformat()
        event = {"event_id": str(uuid.uuid4()), "occurred_at": now, "order_id": order_id, "ready_at": now}
        connection = pika.BlockingConnection(pika.URLParameters(RABBITMQ_URL))
        channel = connection.channel()
        channel.exchange_declare(exchange="panaderia.events", exchange_type="topic", durable=True)
        channel.basic_publish("panaderia.events", "order.ready", json.dumps(event).encode(),
                              pika.BasicProperties(content_type="application/json", delivery_mode=2, message_id=event["event_id"]))
        connection.close()
        print(f"order.ready publicado para {order_id}", flush=True)
    except Exception as exc:  # noqa: BLE001
        print(f"No se pudo publicar order.ready: {exc}", flush=True)


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._send(200, {"status": "ok", "service": "fake-kitchen"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        order = json.loads(self.rfile.read(length) or b"{}")
        print(f"POST {self.path} token={self.headers.get('X-Service-Token')} -> {json.dumps(order, ensure_ascii=False)}", flush=True)
        if FAIL:
            return self._send(503, {"error": "kitchen caída (FAIL=1)"})
        if order.get("order_id") in SEEN:
            return self._send(409, {"error": "ya encolado"})
        SEEN.add(order.get("order_id"))
        if RABBITMQ_URL:
            threading.Timer(READY_AFTER, publish_ready, args=[order["order_id"]]).start()
        self._send(201, {"queued": True})


if __name__ == "__main__":
    print(f"Fake Kitchen en http://localhost:8001 (FAIL={FAIL})", flush=True)
    HTTPServer(("0.0.0.0", 8001), Handler).serve_forever()
