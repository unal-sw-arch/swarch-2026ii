"""One job per worker; FFmpeg runs off the RabbitMQ connection thread."""

from concurrent.futures import ThreadPoolExecutor
import json
import logging
from pathlib import Path
import signal
import threading
import time
from uuid import UUID

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

import pika
from sqlalchemy.exc import SQLAlchemyError

from shared.services.blob_storage import BlobStorageService
from shared.services.database import engine
from shared.services.processing_jobs import LeaseLostError, ProcessingJobService
from shared.services.rabbitmq import RabbitMQService
from encoder_worker.services.transcoding import TranscodingService
from encoder_worker.health import WorkerHealthServer
from shared.services.video_metadata_service import VideoMetadataService

logger = logging.getLogger(__name__)


def parse_message(body: bytes) -> UUID:
    payload = json.loads(body)
    if (not isinstance(payload, dict) or payload.get("schema_version") != 2
        or not isinstance(payload.get("video_id"), str)
        or payload.get("job_id") != payload["video_id"]):
        raise ValueError("Unsupported transcoding job message")
    return UUID(payload["video_id"])


def run_job(transcoder, jobs, claim) -> None:
    try:
        transcoder.process(claim)
    except LeaseLostError:
        logger.warning("Lease lost for video %s; dispatcher handles recovery", claim.video_id)
    except Exception as error:
        logger.exception("Transcoding failed for video %s", claim.video_id)
        try:
            jobs.fail(claim, f"{type(error).__name__}: {error}")
        except LeaseLostError:
            logger.warning("Cannot mark failure after lease loss for video %s", claim.video_id)


def consume(broker, jobs, transcoder, stop) -> None:
    active = None
    last_heartbeat = time.monotonic()
    with ThreadPoolExecutor(max_workers=1) as pool:
        def on_message(channel, method, properties, body):
            nonlocal active, last_heartbeat
            try:
                video_id = parse_message(body)
            except (ValueError, TypeError, UnicodeDecodeError):
                logger.warning("Malformed job sent to failure queue")
                channel.basic_nack(method.delivery_tag, requeue=False)
                return
            claim = jobs.claim(video_id)
            if claim is None:
                # Already claimed/completed or a retry not due yet; durable DB state owns recovery.
                channel.basic_ack(method.delivery_tag)
                return
            active = (pool.submit(run_job, transcoder, jobs, claim), method.delivery_tag, claim)
            last_heartbeat = time.monotonic()

        consumer_tag = broker.channel.basic_consume(
            queue=broker.QUEUE, on_message_callback=on_message, auto_ack=False
        )
        cancelled = False
        while not stop.is_set() or active is not None:
            if stop.is_set() and not cancelled:
                broker.channel.basic_cancel(consumer_tag)
                cancelled = True
            broker.connection.process_data_events(time_limit=1)
            if active is None:
                continue
            future, delivery_tag, claim = active
            if future.done():
                future.result()  # A DB failure must leave the delivery unacknowledged.
                broker.channel.basic_ack(delivery_tag)
                active = None
            elif time.monotonic() - last_heartbeat >= jobs.lease_seconds / 3:
                jobs.heartbeat(claim)
                last_heartbeat = time.monotonic()


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    jobs = ProcessingJobService()
    blob = BlobStorageService()
    transcoder = TranscodingService(blob, jobs)
    broker = RabbitMQService()
    health = WorkerHealthServer(lambda: bool(
        broker.connection and broker.connection.is_open and broker.channel and broker.channel.is_open
    ))
    try:
        transcoder.check_tools()
        health.start()
        while not stop.is_set():
            try:
                VideoMetadataService.initialize_schema()
                broker.connect()
                consume(broker, jobs, transcoder, stop)
            except (pika.exceptions.AMQPError, SQLAlchemyError, LeaseLostError, OSError):
                logger.exception("Worker connection unavailable; reconnecting")
                try:
                    broker.close()
                except pika.exceptions.AMQPError:
                    pass
                stop.wait(5)
    finally:
        try:
            broker.close()
        finally:
            blob.close()
            engine.dispose()
            if health.thread.is_alive():
                health.close()


if __name__ == "__main__":
    main()
