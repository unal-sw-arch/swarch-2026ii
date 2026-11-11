import asyncio
import logging

import aio_pika
from aio_pika import DeliveryMode, ExchangeType
from aio_pika.abc import AbstractIncomingMessage
from jinja2 import TemplateError
from pydantic import ValidationError

from . import catalog
from .config import Settings
from .mailer import Mailer, OutgoingEmail, TransientEmailError
from .rendering import render_email
from .schemas import NotificationMessage

logger = logging.getLogger(__name__)

# Topología (los productores solo necesitan EXCHANGE + SEND_KEY)
EXCHANGE = "notifications"
DLX = "notifications.dlx"
QUEUE = "notifications.email"
RETRY_QUEUE = "notifications.email.retry"
DEAD_QUEUE = "notifications.email.dead"
SEND_KEY = "email.send"
RETRY_KEY = "email.retry"
DEAD_KEY = "email.dead"


class NotificationConsumer:
    def __init__(self, settings: Settings, mailer: Mailer):
        self._s = settings
        self._mailer = mailer
        self._connection: aio_pika.abc.AbstractRobustConnection | None = None
        self._exchange: aio_pika.abc.AbstractExchange | None = None
        self._dlx: aio_pika.abc.AbstractExchange | None = None
        self._ready = False
        self._stopping = asyncio.Event()

    @property
    def connected(self) -> bool:
        return bool(self._ready and self._connection and not self._connection.is_closed)

    async def run(self) -> None:
        while not self._stopping.is_set():
            try:
                await self._setup()
                await self._stopping.wait()
            except asyncio.CancelledError:
                raise
            except Exception:
                self._ready = False
                logger.exception("No se pudo conectar a RabbitMQ; reintentando en 5 s")
                try:
                    await asyncio.wait_for(self._stopping.wait(), timeout=5)
                except asyncio.TimeoutError:
                    pass

    async def stop(self) -> None:
        self._stopping.set()
        self._ready = False
        if self._connection and not self._connection.is_closed:
            await self._connection.close()

    async def _setup(self) -> None:
        self._connection = await aio_pika.connect_robust(self._s.rabbitmq_url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=self._s.prefetch)

        self._exchange = await channel.declare_exchange(EXCHANGE, ExchangeType.DIRECT, durable=True)
        dlx = await channel.declare_exchange(DLX, ExchangeType.DIRECT, durable=True)
        self._dlx = dlx

        queue = await channel.declare_queue(QUEUE, durable=True, arguments={
            "x-dead-letter-exchange": DLX, "x-dead-letter-routing-key": DEAD_KEY})
        await queue.bind(self._exchange, SEND_KEY)

        # Cola de espera: el mensaje reposa RETRY_DELAY_MS y vuelve solo a la cola principal.
        retry = await channel.declare_queue(RETRY_QUEUE, durable=True, arguments={
            "x-message-ttl": self._s.retry_delay_ms,
            "x-dead-letter-exchange": EXCHANGE, "x-dead-letter-routing-key": SEND_KEY})
        await retry.bind(self._exchange, RETRY_KEY)

        dead = await channel.declare_queue(DEAD_QUEUE, durable=True)
        await dead.bind(dlx, DEAD_KEY)

        await queue.consume(self._on_message)
        self._ready = True
        logger.info("Consumer listo en %r (dry_run=%s, sandbox_to=%s)",
                    QUEUE, self._s.dry_run, self._s.sandbox_to)

    async def publish(self, message: NotificationMessage) -> None:
        """Solo para el endpoint de desarrollo /dev/publish."""
        assert self._exchange is not None
        await self._exchange.publish(
            aio_pika.Message(body=message.model_dump_json().encode(), content_type="application/json",
                             delivery_mode=DeliveryMode.PERSISTENT, message_id=message.message_id),
            routing_key=SEND_KEY)

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        retries = int((message.headers or {}).get("x-retry-count", 0))
        try:
            notification = NotificationMessage.model_validate_json(message.body)
            spec = catalog.get_spec(notification.type)
            catalog.validate_data(spec, notification.data)

            emails = []
            for r in notification.recipients:
                subject, html = render_email(spec, notification, r, self._s.frontend_url)
                headers = {"List-Unsubscribe": f"<{r.unsubscribe_url}>"} if r.unsubscribe_url else {}
                emails.append(OutgoingEmail(to=str(r.email), subject=subject, html=html, headers=headers))

            errors = await self._mailer.send_all(emails)

        except (ValidationError, catalog.InvalidMessage, TemplateError) as exc:
            logger.error("Mensaje inválido -> DLQ (%s): %s", message.message_id, exc)
            await message.reject(requeue=False)
            return
        except Exception as exc:
            logger.exception("Error inesperado procesando %s", message.message_id)
            await self._retry_or_dead(message, retries, exc)
            return

        # Resultado por destinatario: solo se reintenta a quienes fallaron (SMTP/Brevo no tienen idempotencia)
        pairs = list(zip(notification.recipients, errors))
        transient = [(r, e) for r, e in pairs if isinstance(e, TransientEmailError)]
        permanent = [(r, e) for r, e in pairs if e is not None and not isinstance(e, TransientEmailError)]
        logger.info("Procesado %s type=%s enviados=%d fallas_temporales=%d fallas_permanentes=%d",
                    notification.message_id, notification.type,
                    len(pairs) - len(transient) - len(permanent), len(transient), len(permanent))

        if permanent:
            for r, e in permanent:
                logger.error("Falla permanente para %s: %s", r.email, e)
            await self._publish_dead(self._subset(notification, [r for r, _ in permanent]), message,
                                     "; ".join(f"{r.email}: {e}" for r, e in permanent))
        if transient:
            await self._retry_or_dead(message, retries, "; ".join(f"{r.email}: {e}" for r, e in transient),
                                      body=self._subset(notification, [r for r, _ in transient]))
            return
        await message.ack()

    @staticmethod
    def _subset(notification: NotificationMessage, recipients: list) -> bytes:
        return notification.model_copy(update={"recipients": recipients}).model_dump_json().encode()

    async def _publish_dead(self, body: bytes, message: AbstractIncomingMessage, reason) -> None:
        assert self._dlx is not None
        await self._dlx.publish(
            aio_pika.Message(body=body, content_type="application/json",
                             delivery_mode=DeliveryMode.PERSISTENT, message_id=message.message_id,
                             headers={**(message.headers or {}), "x-failure-reason": str(reason)[:500]}),
            routing_key=DEAD_KEY)

    async def _retry_or_dead(self, message: AbstractIncomingMessage, retries: int, reason,
                             body: bytes | None = None) -> None:
        body = body or message.body
        if retries >= self._s.max_retries:
            logger.error("Reintentos agotados (%d) -> DLQ (%s): %s", retries, message.message_id, reason)
            await self._publish_dead(body, message, reason)
            await message.ack()
            return
        logger.warning("Falla temporal (%s). Reintento %d/%d en %d ms",
                       reason, retries + 1, self._s.max_retries, self._s.retry_delay_ms)
        assert self._exchange is not None
        await self._exchange.publish(
            aio_pika.Message(body=body, content_type="application/json",
                             delivery_mode=DeliveryMode.PERSISTENT, message_id=message.message_id,
                             headers={**(message.headers or {}), "x-retry-count": retries + 1}),
            routing_key=RETRY_KEY)
        await message.ack()
