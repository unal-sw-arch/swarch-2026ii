"""Configuración del Worker. Los nombres de variables son los de docker-compose.p2.yml / .env.p2.example."""
import os
from urllib.parse import urlparse


def _bool(name: str, default: str) -> bool:
    return os.getenv(name, default).strip().lower() in ("1", "true", "yes", "on")


def _first(*names: str, default: str = "") -> str:
    for name in names:
        if os.getenv(name):
            return os.environ[name]
    return default


# --- RabbitMQ (topología definida en infrastructure/rabbitmq/definitions.json) ---
RABBITMQ_URL = _first("RABBITMQ_URL", default="amqp://panaderia:panaderia@rabbitmq:5672/")
BROKER_URL = _first("CELERY_BROKER_URL", "RABBITMQ_URL", default=RABBITMQ_URL)
RABBITMQ_EXCHANGE = os.getenv("RABBITMQ_EXCHANGE", "panaderia.events")
DLX_EXCHANGE = _first("RABBITMQ_DLX", default="panaderia.dlx")
DLQ_QUEUE = _first("RABBITMQ_DLQ", "DLQ_QUEUE", default="panaderia.dlq")
TASK_QUEUE = os.getenv("TASK_QUEUE", "worker.tasks")  # cola interna de Celery

# Una cola por evento, como en definitions.json. `worker.reservation_confirmed` aún no existe allí:
# se activa agregándola a EVENTS_QUEUES cuando Jaime la incluya.
QUEUE_BINDINGS = {
    "worker.order_paid": "order.paid",
    "worker.order_ready": "order.ready",
    "worker.reservation_created": "reservation.created",
    "worker.reservation_cancelled": "reservation.cancelled",
    "worker.reservation_confirmed": "reservation.confirmed",
}
EVENTS_QUEUES = [
    q.strip()
    for q in os.getenv(
        "EVENTS_QUEUES",
        "worker.order_paid,worker.order_ready,worker.reservation_created,worker.reservation_cancelled",
    ).split(",")
    if q.strip()
]
# true solo en la demo sin Jaime: el Worker declara las colas con los mismos argumentos que definitions.json.
DECLARE_TOPOLOGY = _bool("DECLARE_TOPOLOGY", "false")

# --- Servicios ---
KITCHEN_URL = os.getenv("KITCHEN_URL", "http://kitchen-service:8001")
ORDER_SERVICE_URL = _first("ORDER_SERVICE_URL", "ORDER_URL", default="http://order-service:8000")
SERVICE_TOKEN = os.getenv("SERVICE_TOKEN", "")
HTTP_TIMEOUT = float(os.getenv("HTTP_TIMEOUT_SECONDS", "10"))

# --- MongoDB (la base sale de la ruta de la URL) ---
MONGODB_URL = _first("MONGODB_URL", "MONGO_URL", default="mongodb://mongodb:27017/panaderia")
MONGO_DB_DEFAULT = os.getenv("MONGO_DB", "panaderia")

# --- Almacenamiento S3 (RustFS) ---
_endpoint = urlparse(_first("S3_ENDPOINT", default="http://rustfs:9000"))
S3_ENDPOINT_HOST = _endpoint.netloc or _endpoint.path       # host:puerto, sin esquema
S3_SECURE = _endpoint.scheme == "https" or _bool("S3_USE_SSL", "false")
S3_ACCESS_KEY = os.getenv("S3_ACCESS_KEY", "panaderia")
S3_SECRET_KEY = os.getenv("S3_SECRET_KEY", "panaderia123")
S3_REGION = os.getenv("S3_REGION", "us-east-1")
RECEIPTS_BUCKET = _first("S3_BUCKET_RECEIPTS", "RECEIPTS_BUCKET", default="receipts")

MAX_RETRIES = int(_first("WORKER_MAX_RETRIES", "MAX_RETRIES", default="3"))

# Eventos de dominio que consume el Worker (routing key → tarea Celery).
EVENT_TASKS = {
    "order.paid": "worker.process_order_paid",
    "order.ready": "worker.process_order_ready",
    "reservation.created": "worker.process_reservation_created",
    "reservation.confirmed": "worker.process_reservation_confirmed",
    "reservation.cancelled": "worker.process_reservation_cancelled",
}
