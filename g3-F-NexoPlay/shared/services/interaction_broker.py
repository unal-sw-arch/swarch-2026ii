"""RabbitMQ topology for interaction events and a confirmed, thread-safe publisher."""

import logging
import threading

import pika

from shared.services.interaction_events import InteractionEvent
from shared.services.rabbitmq import connection_parameters

logger = logging.getLogger(__name__)


class InteractionBroker:
    EXCHANGE = "interactions"
    QUEUE = "interactions.events"
    ROUTING_KEY = "event"
    FAILED_EXCHANGE = "interactions.failed"
    FAILED_QUEUE = "interactions.events.failed"
    FAILED_ROUTING_KEY = "failed"

    def __init__(self, prefetch: int = 10):
        self.prefetch = prefetch
        self.connection = None
        self.channel = None

    @property
    def is_open(self) -> bool:
        return bool(self.connection and self.connection.is_open
                    and self.channel and self.channel.is_open)

    def connect(self) -> None:
        self.connection = pika.BlockingConnection(connection_parameters())
        channel = self.connection.channel()
        channel.exchange_declare(exchange=self.EXCHANGE, exchange_type="direct", durable=True)
        channel.exchange_declare(exchange=self.FAILED_EXCHANGE, exchange_type="direct", durable=True)
        channel.queue_declare(queue=self.FAILED_QUEUE, durable=True)
        channel.queue_bind(queue=self.FAILED_QUEUE, exchange=self.FAILED_EXCHANGE,
                           routing_key=self.FAILED_ROUTING_KEY)
        channel.queue_declare(
            queue=self.QUEUE, durable=True,
            arguments={"x-dead-letter-exchange": self.FAILED_EXCHANGE,
                       "x-dead-letter-routing-key": self.FAILED_ROUTING_KEY},
        )
        channel.queue_bind(queue=self.QUEUE, exchange=self.EXCHANGE, routing_key=self.ROUTING_KEY)
        channel.basic_qos(prefetch_count=self.prefetch)
        channel.confirm_delivery()
        self.channel = channel

    def publish(self, event: InteractionEvent) -> None:
        """Return only after the broker confirmed the message; raise AMQPError otherwise."""
        self.channel.basic_publish(
            exchange=self.EXCHANGE,
            routing_key=self.ROUTING_KEY,
            body=event.to_bytes(),
            properties=pika.BasicProperties(
                content_type="application/json", delivery_mode=2,
                message_id=str(event.event_id), type=event.type,
            ),
            mandatory=True,
        )

    def close(self) -> None:
        if self.connection is not None and self.connection.is_open:
            self.connection.close()
        self.connection = None
        self.channel = None


class EventPublishError(RuntimeError):
    pass


class InteractionPublisher:
    """One shared connection for a threaded web server.

    pika's BlockingConnection is not thread-safe, so every use goes through a lock.
    A background thread services heartbeats so an idle connection is not dropped.
    """

    def __init__(self, attempts: int = 3, heartbeat_interval: float = 10.0,
                 broker_factory=InteractionBroker):
        self.attempts = attempts
        self.heartbeat_interval = heartbeat_interval
        self.broker_factory = broker_factory
        self.broker = None
        self.lock = threading.Lock()
        self.stop = threading.Event()
        self.thread = None

    def start(self) -> None:
        self.thread = threading.Thread(target=self._service_heartbeats, daemon=True)
        self.thread.start()

    def _ensure_connected(self):
        if self.broker is None or not self.broker.is_open:
            self._discard()
            broker = self.broker_factory()
            broker.connect()
            self.broker = broker
        return self.broker

    def _discard(self) -> None:
        if self.broker is not None:
            try:
                self.broker.close()
            except (pika.exceptions.AMQPError, OSError):
                pass
        self.broker = None

    def publish(self, event: InteractionEvent) -> None:
        last_error = None
        with self.lock:
            for attempt in range(1, self.attempts + 1):
                try:
                    self._ensure_connected().publish(event)
                    return
                except (pika.exceptions.AMQPError, OSError) as error:
                    last_error = error
                    logger.warning("Publish attempt %d/%d for event %s failed: %r",
                                   attempt, self.attempts, event.event_id, error)
                    self._discard()
        raise EventPublishError("Interaction broker is unavailable.") from last_error

    def check(self) -> bool:
        with self.lock:
            try:
                self._ensure_connected()
                return True
            except (pika.exceptions.AMQPError, OSError):
                self._discard()
                return False

    def _service_heartbeats(self) -> None:
        while not self.stop.wait(self.heartbeat_interval):
            with self.lock:
                if self.broker is None or not self.broker.is_open:
                    continue
                try:
                    self.broker.connection.process_data_events(time_limit=0)
                except (pika.exceptions.AMQPError, OSError):
                    self._discard()

    def close(self) -> None:
        self.stop.set()
        if self.thread is not None:
            self.thread.join(timeout=2)
        with self.lock:
            self._discard()
