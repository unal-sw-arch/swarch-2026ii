"""Deliver committed video snapshots; never depend on RabbitMQ availability."""

import argparse
import logging
from pathlib import Path
import signal
import threading

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError

from shared.services.database import SessionLocal, engine
from shared.services.embedding_notifications import EmbeddingNotification, EmbeddingNotificationService, utcnow
from shared.services.embeddings_client import EmbeddingsClient
from shared.services.video_metadata_service import VideoMetadataRecord, VideoMetadataService


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backfill", action="store_true", help="Queue existing videos that have no notification; then exit")
    parser.add_argument("--retry-failed", action="store_true", help="Requeue corrected permanent failures; then exit")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    client = EmbeddingsClient()
    try:
        VideoMetadataService.initialize_schema()
        if args.backfill or args.retry_failed:
            with SessionLocal.begin() as session:
                if args.backfill:
                    for video in session.scalars(select(VideoMetadataRecord).where(
                        ~select(EmbeddingNotification.video_id).where(
                            EmbeddingNotification.video_id == VideoMetadataRecord.id).exists()
                    ).with_for_update()).all():
                        EmbeddingNotificationService.enqueue(session, video)
                if args.retry_failed:
                    for row in session.scalars(select(EmbeddingNotification).where(
                        EmbeddingNotification.status == "FAILED").with_for_update()).all():
                        row.status, row.next_attempt_at = "PENDING", utcnow()
            return
        outbox = EmbeddingNotificationService()
        while not stop.is_set():
            try:
                # One HTTP request per iteration bounds shutdown and DB lock time.
                delivered = outbox.deliver_one(client)
                if not delivered:
                    stop.wait(1)
            except (SQLAlchemyError, OSError):
                logging.exception("Embedding dispatcher unavailable; notifications remain in PostgreSQL")
                stop.wait(5)
    finally:
        client.close()
        engine.dispose()


if __name__ == "__main__":
    main()
