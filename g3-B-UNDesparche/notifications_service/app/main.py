import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from .catalog import CATALOG
from .config import load_settings
from .consumer import DEAD_QUEUE, EXCHANGE, QUEUE, SEND_KEY, NotificationConsumer
from .mailer import Mailer
from .schemas import NotificationMessage

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

settings = load_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    mailer = Mailer(settings)
    consumer = NotificationConsumer(settings, mailer)
    app.state.consumer = consumer
    task = asyncio.create_task(consumer.run(), name="notifications-consumer")
    yield
    await consumer.stop()
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)
    await mailer.close()


app = FastAPI(title="UNdesparche - Notifications", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health():
    ok = app.state.consumer.connected
    body = {"status": "ok" if ok else "degraded", "rabbitmq": ok, "provider": settings.provider,
            "email_mode": "dry-run" if settings.dry_run else ("sandbox" if settings.sandbox_to else "live")}
    if not ok:
        raise HTTPException(status_code=503, detail=body)
    return body


@app.get("/types")
async def types():
    """Contrato: tipos soportados y campos obligatorios de `data`."""
    return {
        "exchange": EXCHANGE, "routing_key": SEND_KEY, "queue": QUEUE, "dead_letter_queue": DEAD_QUEUE,
        "types": {k: {"source": v.source, "required_data": list(v.required)} for k, v in CATALOG.items()},
    }


if settings.dev_endpoints:
    @app.post("/dev/publish", status_code=202)
    async def dev_publish(message: NotificationMessage):
        """Publica un mensaje en la cola como si fuera Events/Inventory (solo desarrollo)."""
        if not app.state.consumer.connected:
            raise HTTPException(status_code=503, detail="RabbitMQ no disponible")
        await app.state.consumer.publish(message)
        return {"queued": message.message_id}
