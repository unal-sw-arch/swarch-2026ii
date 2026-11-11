"""Consume interaction events, persist them in MongoDB and trigger profile updates."""

import logging
from pathlib import Path
import signal
import threading

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

import pika
from pymongo.errors import PyMongoError

from shared.services.health_server import WorkerHealthServer
from shared.services.interaction_broker import InteractionBroker
from shared.services.interaction_events import InteractionEvent
from shared.services.interaction_store import InteractionStore
from interaction_worker.services.apply_event import EventApplier
from interaction_worker.services.profile_updater import ProfileUpdater

logger = logging.getLogger(__name__)


def handle_message(channel, method, body, applier: EventApplier, updater: ProfileUpdater) -> None:
    try:
        event = InteractionEvent.from_bytes(body)
    except ValueError as error:
        logger.warning("Malformed interaction event sent to failure queue: %s", error)
        channel.basic_nack(method.delivery_tag, requeue=False)
        return
    # A MongoDB error propagates: the delivery stays unacknowledged and is redelivered.
    applier.apply(event)
    # Persist the scheduling checkpoint before acknowledging. If either step
    # fails, RabbitMQ redelivers and event_id prevents counting it twice.
    updater.note_activity(str(event.user_id))
    channel.basic_ack(method.delivery_tag)


def consume(broker: InteractionBroker, applier: EventApplier, updater: ProfileUpdater,
            stop: threading.Event) -> None:
    def on_message(channel, method, properties, body):
        handle_message(channel, method, body, applier, updater)

    consumer_tag = broker.channel.basic_consume(
        queue=broker.QUEUE, on_message_callback=on_message, auto_ack=False,
    )
    while not stop.is_set():
        broker.connection.process_data_events(time_limit=1)
    broker.channel.basic_cancel(consumer_tag)


def profile_loop(updater: ProfileUpdater, stop: threading.Event) -> None:
    """Slow synchronous inference must not block RabbitMQ heartbeats/ingestion."""
    while not stop.is_set():
        try:
            updater.sweep(batch=1)
        except Exception:
            # The durable state/lease survives an interrupted attempt. A later
            # pass retries it, including after a transient MongoDB outage.
            logger.exception("Profile sweep failed; pending work remains durable")
        stop.wait(updater.settings.sweep_seconds)


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    store = InteractionStore()
    applier = EventApplier(store)
    updater = ProfileUpdater(store)
    broker = InteractionBroker()
    health = WorkerHealthServer(lambda: broker.is_open)
    profile_thread = None
    try:
        health.start()
        while not stop.is_set():
            try:
                store.ensure_indexes()
                if profile_thread is None:
                    profile_thread = threading.Thread(target=profile_loop, args=(updater, stop), daemon=True)
                    profile_thread.start()
                broker.connect()
                logger.info("Interaction worker consuming from %s", broker.QUEUE)
                consume(broker, applier, updater, stop)
            except (pika.exceptions.AMQPError, PyMongoError, OSError):
                logger.exception("Worker connection unavailable; reconnecting")
                try:
                    broker.close()
                except (pika.exceptions.AMQPError, OSError):
                    pass
                stop.wait(5)
    finally:
        stop.set()
        if profile_thread is not None:
            profile_thread.join(timeout=updater.settings.connect_timeout + updater.settings.request_timeout + 10)
        try:
            broker.close()
        finally:
            updater.close()
            store.close()
            health.close()


if __name__ == "__main__":
    main()
