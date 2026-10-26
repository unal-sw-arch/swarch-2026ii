"""Publish PostgreSQL outbox jobs and recover workers whose leases expired."""

import logging
from pathlib import Path
import signal
import threading

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

import pika
from sqlalchemy.exc import SQLAlchemyError

from shared.services.database import engine
from shared.services.processing_jobs import ProcessingJobService
from shared.services.rabbitmq import RabbitMQService
from shared.services.video_metadata_service import VideoMetadataService

logger = logging.getLogger(__name__)


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    jobs = ProcessingJobService()
    broker = RabbitMQService()
    try:
        while not stop.is_set():
            try:
                VideoMetadataService.initialize_schema()
                broker.connect()
                while not stop.is_set():
                    jobs.recover_expired()
                    # Bounded batch keeps heartbeat processing and shutdown responsive.
                    for _ in range(25):
                        if stop.is_set() or not jobs.publish_one(broker):
                            break
                    broker.connection.process_data_events(time_limit=1)
                    stop.wait(1)
            except (pika.exceptions.AMQPError, SQLAlchemyError, OSError):
                logger.exception("Dispatcher unavailable; pending jobs remain in PostgreSQL")
                try:
                    broker.close()
                except pika.exceptions.AMQPError:
                    pass
                stop.wait(5)
    finally:
        try:
            broker.close()
        finally:
            engine.dispose()


if __name__ == "__main__":
    main()
