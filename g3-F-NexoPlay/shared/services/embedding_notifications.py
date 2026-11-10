"""Transactional, coalescing video outbox independent of transcoding jobs.

The UUID/version row survives source deletion. A row lock serializes delivery
and replacement, including the uncertain outcome of a lost HTTP response.
"""

from datetime import datetime, timedelta, timezone
import os
from uuid import UUID

from sqlalchemy import BigInteger, CheckConstraint, DateTime, Integer, JSON, String, Uuid, select
from sqlalchemy.orm import Mapped, mapped_column

from shared.services.database import Base, SessionLocal
from shared.services.embeddings_client import EmbeddingsDeliveryError


MAX_VERSION = 9_007_199_254_740_991


def utcnow():
    return datetime.now(timezone.utc)


class EmbeddingNotification(Base):
    __tablename__ = "video_embedding_notifications"
    __table_args__ = (
        CheckConstraint(f"metadata_version > 0 AND metadata_version <= {MAX_VERSION}"),
        CheckConstraint("operation IN ('UPSERT', 'INVALIDATE')"),
        CheckConstraint("status IN ('PENDING', 'DELIVERED', 'FAILED')"),
    )

    # Intentionally no FK: invalidations and version fences survive video deletion.
    video_id: Mapped[UUID] = mapped_column(Uuid, primary_key=True)
    metadata_version: Mapped[int] = mapped_column(BigInteger, nullable=False)
    operation: Mapped[str] = mapped_column(String(12), nullable=False)
    snapshot: Mapped[dict] = mapped_column(JSON, nullable=False)
    status: Mapped[str] = mapped_column(String(12), nullable=False, default="PENDING")
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, index=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    delivered_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_error: Mapped[str | None] = mapped_column(String(100))
    result: Mapped[dict | None] = mapped_column(JSON)


class EmbeddingNotificationService:
    def __init__(self, session_factory=None):
        self.sessions = session_factory or SessionLocal
        self.retry_seconds = int(os.getenv("EMBEDDINGS_RETRY_SECONDS", "2"))
        if not 1 <= self.retry_seconds <= 60:
            raise ValueError("EMBEDDINGS_RETRY_SECONDS must be between 1 and 60")

    @staticmethod
    def enqueue(session, video, *, invalidate=False):
        row = session.scalar(select(EmbeddingNotification).where(
            EmbeddingNotification.video_id == video.id).with_for_update())
        metadata = {"title": video.title, "description": video.description, "category": video.category}
        operation = "INVALIDATE" if invalidate else "UPSERT"
        if row:
            if row.operation == "INVALIDATE":
                if invalidate:
                    return row
                raise ValueError("A deleted video UUID cannot be reused")
            if not invalidate and row.snapshot.get("metadata") == metadata:
                return row
            version = row.metadata_version + 1
        else:
            version = 2 if invalidate else 1
        if version > MAX_VERSION:
            raise ValueError("Metadata version exhausted")
        snapshot = {"contract_version": "1.0", "video_id": str(video.id), "metadata_version": version}
        if not invalidate:
            snapshot["metadata"] = metadata
        if row is None:
            row = EmbeddingNotification(video_id=video.id)
            session.add(row)
        row.metadata_version, row.operation, row.snapshot = version, operation, snapshot
        row.status, row.attempts, row.last_error, row.result = "PENDING", 0, None, None
        row.updated_at = row.next_attempt_at = utcnow()
        row.delivered_at = None
        return row

    def deliver_one(self, client, video_id=None):
        with self.sessions.begin() as session:
            query = select(EmbeddingNotification).where(
                EmbeddingNotification.status == "PENDING", EmbeddingNotification.next_attempt_at <= utcnow())
            if video_id is not None:
                query = query.where(EmbeddingNotification.video_id == video_id)
            row = session.scalar(query.order_by(EmbeddingNotification.next_attempt_at)
                                 .with_for_update(skip_locked=True).limit(1))
            if row is None:
                return False
            row.attempts += 1
            row.updated_at = utcnow()
            try:
                result = (client.video(row.snapshot) if row.operation == "UPSERT"
                          else client.invalidate_video(row.video_id, row.metadata_version))
            except EmbeddingsDeliveryError as error:
                row.last_error = error.code[:100]
                if error.retryable:
                    delay = min(60, self.retry_seconds * 2 ** min(row.attempts - 1, 6))
                    row.next_attempt_at = utcnow() + timedelta(seconds=delay)
                else:
                    row.status = "FAILED"
            else:
                row.result, row.status, row.last_error = result, "DELIVERED", None
                row.delivered_at = utcnow()
            return True
