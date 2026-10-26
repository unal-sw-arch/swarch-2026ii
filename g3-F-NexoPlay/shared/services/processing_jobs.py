"""Durable job/outbox records and fenced worker leases.

No RabbitMQ operation happens during an upload. The raw location and its job
are committed together; the dispatcher publishes that job with confirmations.
"""

import os
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, JSON, String, Text, Uuid, select
from sqlalchemy.orm import Mapped, mapped_column

from shared.services.database import Base, SessionLocal
from shared.services.video_metadata_service import VideoMetadataRecord, VideoMetadataService


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class LeaseLostError(RuntimeError):
    pass


class ProcessingJob(Base):
    __tablename__ = "video_processing_jobs"

    video_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("video_metadata.id", ondelete="CASCADE"), primary_key=True
    )
    storage_bucket: Mapped[str] = mapped_column(String(63), nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="PENDING", index=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    max_attempts: Mapped[int] = mapped_column(Integer, default=3)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    failure_published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    lease_token: Mapped[str | None] = mapped_column(String(36))
    lease_expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)
    error: Mapped[str | None] = mapped_column(Text)
    source_metadata: Mapped[dict | None] = mapped_column(JSON)


class VideoVariant(Base):
    __tablename__ = "video_variants"

    video_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("video_metadata.id", ondelete="CASCADE"), primary_key=True
    )
    profile: Mapped[str] = mapped_column(String(32), primary_key=True)
    format: Mapped[str] = mapped_column(String(10))
    width: Mapped[int] = mapped_column(Integer)
    height: Mapped[int] = mapped_column(Integer)
    video_codec: Mapped[str] = mapped_column(String(40))
    audio_codec: Mapped[str | None] = mapped_column(String(40))
    duration: Mapped[float] = mapped_column()
    size_bytes: Mapped[int] = mapped_column(BigInteger)
    storage_bucket: Mapped[str] = mapped_column(String(63))
    storage_object_key: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class VideoHLSRecord(Base):
    __tablename__ = "video_hls_packages"

    video_id: Mapped[UUID] = mapped_column(
        Uuid, ForeignKey("video_metadata.id", ondelete="CASCADE"), primary_key=True
    )
    manifest: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


@dataclass(frozen=True)
class ClaimedJob:
    video_id: UUID
    token: str
    bucket: str
    object_key: str
    completed_profiles: frozenset[str]
    hls_completed: bool = False
    poster_completed: bool = False


class ProcessingJobService:
    def __init__(self, session_factory=None):
        self.sessions = session_factory or SessionLocal
        self.lease_seconds = int(os.getenv("WORKER_LEASE_SECONDS", "120"))
        self.retry_seconds = int(os.getenv("TRANSCODE_RETRY_SECONDS", "15"))
        if self.lease_seconds < 30 or self.retry_seconds < 1:
            raise ValueError("Lease must be >= 30 seconds and retry delay >= 1 second")

    @staticmethod
    def enqueue(session, video_id: UUID, bucket: str) -> None:
        max_attempts = int(os.getenv("TRANSCODE_MAX_ATTEMPTS", "3"))
        if max_attempts < 1:
            raise ValueError("TRANSCODE_MAX_ATTEMPTS must be >= 1")
        session.add(ProcessingJob(video_id=video_id, storage_bucket=bucket, max_attempts=max_attempts))

    def claim(self, video_id: UUID) -> ClaimedJob | None:
        now = utcnow()
        with self.sessions.begin() as session:
            job = session.scalar(
                select(ProcessingJob).where(
                    ProcessingJob.video_id == video_id,
                    ProcessingJob.status == "PENDING",
                    ProcessingJob.next_attempt_at <= now,
                ).with_for_update()
            )
            if job is None:
                return None
            video = session.get(VideoMetadataRecord, video_id)
            if video is None or not job.storage_bucket or not video.original_object_key:
                raise LookupError(f"Raw location missing for video {video_id}")
            job.status = "PROCESSING"
            video.status = "PROCESSING"
            video.processed_at = None
            job.attempts += 1
            job.started_at = job.started_at or now
            job.updated_at = now
            job.lease_token = str(uuid4())
            job.lease_expires_at = now + timedelta(seconds=self.lease_seconds)
            profiles = session.scalars(
                select(VideoVariant.profile).where(VideoVariant.video_id == video_id)
            ).all()
            return ClaimedJob(video_id, job.lease_token, job.storage_bucket,
                              video.original_object_key, frozenset(profiles),
                              session.get(VideoHLSRecord, video_id) is not None,
                              bool((job.source_metadata or {}).get("poster")))

    @staticmethod
    def _owned(session, claim: ClaimedJob) -> ProcessingJob:
        job = session.scalar(
            select(ProcessingJob).where(
                ProcessingJob.video_id == claim.video_id,
                ProcessingJob.status == "PROCESSING",
                ProcessingJob.lease_token == claim.token,
                ProcessingJob.lease_expires_at > utcnow(),
            ).with_for_update()
        )
        if job is None:
            raise LeaseLostError(f"Lease lost for video {claim.video_id}")
        return job

    def heartbeat(self, claim: ClaimedJob) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            job.lease_expires_at = utcnow() + timedelta(seconds=self.lease_seconds)
            job.updated_at = utcnow()

    def record_source(self, claim: ClaimedJob, source: dict) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            job.source_metadata = {**(job.source_metadata or {}), **source}
            job.updated_at = utcnow()
            video = session.get(VideoMetadataRecord, claim.video_id)
            video.duration_seconds = Decimal(str(source["duration"])).quantize(Decimal("0.001"))
            video.width = source.get("source_width", source["width"])
            video.height = source.get("source_height", source["height"])

    def record_variant(self, claim: ClaimedJob, data: dict) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            session.add(VideoVariant(video_id=claim.video_id, **data))
            job.updated_at = utcnow()

    def complete(self, claim: ClaimedJob) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            video = session.get(VideoMetadataRecord, claim.video_id)
            if (not video.manifest_object_key or not video.thumbnail_object_key
                or not (job.source_metadata or {}).get("poster")):
                raise ValueError("Cannot complete a job before HLS and poster are registered")
            job.status = "COMPLETED"
            video.status = "COMPLETED"
            job.completed_at = job.updated_at = utcnow()
            video.processed_at = job.completed_at
            job.error = None
            job.lease_token = job.lease_expires_at = None

    def record_hls(self, claim: ClaimedJob, manifest: dict) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            session.add(VideoHLSRecord(video_id=claim.video_id, manifest=manifest))
            session.get(VideoMetadataRecord, claim.video_id).manifest_object_key = manifest["master_object_key"]
            for rendition in manifest["renditions"]:
                session.add(VideoVariant(
                    video_id=claim.video_id, profile=rendition["profile"], format="hls",
                    width=rendition["width"], height=rendition["height"],
                    video_codec=rendition["video_codec"], audio_codec=rendition["audio_codec"],
                    duration=rendition["duration"], size_bytes=rendition["size_bytes"],
                    storage_bucket=manifest["bucket"], storage_object_key=rendition["playlist_object_key"],
                ))
            job.updated_at = utcnow()

    @contextmanager
    def artifact_lock(self, claim: ClaimedJob):
        """Fence writes to the stable blob keys with the same lock used by recovery."""
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            yield
            job.lease_expires_at = utcnow() + timedelta(seconds=self.lease_seconds)
            job.updated_at = utcnow()

    def record_poster(self, claim: ClaimedJob, poster: dict) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            job.source_metadata = {**(job.source_metadata or {}), "poster": poster}
            session.get(VideoMetadataRecord, claim.video_id).thumbnail_object_key = poster["object_key"]
            job.updated_at = utcnow()

    def get_variant(self, video_id: UUID, profile: str) -> dict:
        with self.sessions() as session:
            variant = session.get(VideoVariant, (video_id, profile))
            if variant is None:
                raise LookupError(f"Missing variant {profile} for video {video_id}")
            return {"bucket": variant.storage_bucket, "object_key": variant.storage_object_key}

    def fail(self, claim: ClaimedJob, error: str) -> None:
        with self.sessions.begin() as session:
            job = self._owned(session, claim)
            self._schedule_failure(job, error)
            video = session.get(VideoMetadataRecord, claim.video_id)
            video.status = job.status
            video.processed_at = job.completed_at if job.status == "FAILED" else None

    def _schedule_failure(self, job: ProcessingJob, error: str) -> None:
        now = utcnow()
        job.error = error[-4000:]
        job.updated_at = now
        job.lease_token = job.lease_expires_at = None
        if job.attempts >= job.max_attempts:
            job.status = "FAILED"
            job.completed_at = now
        else:
            job.status = "PENDING"
            job.published_at = None
            job.next_attempt_at = now + timedelta(
                seconds=self.retry_seconds * 2 ** min(job.attempts - 1, 8)
            )

    def recover_expired(self) -> int:
        with self.sessions.begin() as session:
            jobs = session.scalars(
                select(ProcessingJob).where(
                    ProcessingJob.status == "PROCESSING",
                    ProcessingJob.lease_expires_at <= utcnow(),
                ).with_for_update(skip_locked=True).limit(100)
            ).all()
            for job in jobs:
                self._schedule_failure(job, "Worker lease expired; worker may have stopped.")
                video = session.get(VideoMetadataRecord, job.video_id)
                video.status = job.status
                video.processed_at = job.completed_at if job.status == "FAILED" else None
            return len(jobs)

    def publish_one(self, broker) -> bool:
        """Hold row lock until confirmation; crashes may duplicate but cannot lose a job."""
        with self.sessions.begin() as session:
            job = session.scalar(
                select(ProcessingJob).where(
                    ((ProcessingJob.status == "PENDING")
                     & ProcessingJob.published_at.is_(None)
                     & (ProcessingJob.next_attempt_at <= utcnow()))
                    | ((ProcessingJob.status == "FAILED")
                       & ProcessingJob.failure_published_at.is_(None))
                ).order_by(ProcessingJob.created_at).with_for_update(skip_locked=True).limit(1)
            )
            if job is None:
                return False
            payload = {"schema_version": 2, "job_id": str(job.video_id), "video_id": str(job.video_id)}
            failed = job.status == "FAILED"
            if failed:
                payload.update(attempts=job.attempts, error=job.error)
            broker.publish(payload, failed=failed)
            if failed:
                job.failure_published_at = utcnow()
            else:
                job.published_at = utcnow()
            return True

    def get(self, video_id: UUID) -> dict | None:
        with self.sessions() as session:
            video = session.get(VideoMetadataRecord, video_id)
            if video is None:
                return None
            job = session.get(ProcessingJob, video_id)
            hls = session.get(VideoHLSRecord, video_id)
            variants = session.scalars(
                select(VideoVariant).where(VideoVariant.video_id == video_id)
                .order_by(VideoVariant.profile)
            ).all()
            def fields(record, names):
                return {name: getattr(record, name) for name in names.split()}
            return {
                "video_id": video.id,
                "metadata": VideoMetadataService.serialize(video),
                "raw": {"bucket": job.storage_bucket if job else None, "object_key": video.original_object_key},
                "processing": fields(job, "status attempts max_attempts created_at updated_at "
                                     "started_at completed_at next_attempt_at error source_metadata")
                              if job else None,
                "variants": [fields(v, "profile format width height video_codec audio_codec "
                                    "duration size_bytes storage_bucket storage_object_key created_at")
                             for v in variants],
                "hls": {**hls.manifest, "playback_path": f"/videos/{video_id}/hls/master.m3u8"} if hls else None,
                "thumbnail": {**job.source_metadata["poster"], "playback_path": f"/videos/{video_id}/thumbnails/poster.jpg"}
                             if job and job.source_metadata and job.source_metadata.get("poster") else None,
            }
