from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import BigInteger, CheckConstraint, DateTime, Integer, Numeric, String, Text, Uuid, func, inspect, text
from sqlalchemy.orm import Mapped, mapped_column

from shared.dto.video_metadata import VideoMetadata
from shared.services.database import Base, SessionLocal, engine


class VideoMetadataRecord(Base):
    __tablename__ = "video_metadata"
    __table_args__ = (CheckConstraint(
        "status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')", name="video_metadata_status_check"
    ),)

    id: Mapped[UUID] = mapped_column(Uuid, primary_key=True, default=uuid4)
    owner_id: Mapped[UUID] = mapped_column(Uuid, nullable=False)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    category: Mapped[str | None] = mapped_column(String(100), nullable=True)
    status: Mapped[str] = mapped_column(String(30), nullable=False, default="PENDING")
    original_object_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    manifest_object_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    thumbnail_object_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    duration_seconds: Mapped[Decimal | None] = mapped_column(Numeric(12, 3), nullable=True)
    size_bytes: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    original_filename: Mapped[str | None] = mapped_column(String(255), nullable=True)
    content_type: Mapped[str | None] = mapped_column(String(100), nullable=True)
    width: Mapped[int | None] = mapped_column(Integer, nullable=True)
    height: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False,
        default=lambda: datetime.now(timezone.utc), server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc),
        server_default=func.now(), onupdate=lambda: datetime.now(timezone.utc),
    )
    processed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class VideoMetadataService:
    """Persists and retrieves video metadata through SQLAlchemy's ORM."""

    @staticmethod
    def initialize_schema() -> None:
        from shared.services import processing_jobs  # noqa: F401

        with engine.begin() as connection:
            if connection.dialect.name == "postgresql":
                # Serialize cold starts of independently deployed API/worker containers.
                connection.execute(text("SELECT pg_advisory_xact_lock(80865672)"))
            schema = inspect(connection)
            if schema.has_table("video_metadata"):
                columns = {c["name"]: c for c in schema.get_columns("video_metadata")}
                missing = set(VideoMetadataRecord.__table__.columns.keys()) - columns.keys()
                if missing or not isinstance(columns["id"]["type"], Uuid):
                    raise RuntimeError(
                        "Incompatible video metadata schema detected. "
                        f"Missing columns: {', '.join(sorted(missing)) or 'none'}; "
                        f"id type: {columns['id']['type']}. For disposable test data, apply "
                        "processing_service/migrations/001_reset_video_metadata.sql before starting services."
                    )
            Base.metadata.create_all(bind=connection)

    @staticmethod
    def create(
        metadata: VideoMetadata,
        owner_id: UUID,
        filename: str,
        content_type: str,
        size_bytes: int,
    ) -> UUID:
        record = VideoMetadataRecord(
            owner_id=owner_id,
            title=metadata.title,
            description=metadata.description,
            category=metadata.category,
            status="PENDING",
            original_filename=filename,
            content_type=content_type,
            size_bytes=size_bytes,
        )

        with SessionLocal.begin() as session:
            session.add(record)
            session.flush()
            return record.id

    @staticmethod
    def set_storage_location(video_id: UUID, bucket: str, object_key: str) -> dict:
        from shared.services.processing_jobs import ProcessingJobService

        with SessionLocal.begin() as session:
            record = session.get(VideoMetadataRecord, video_id)
            if record is None:
                raise LookupError(f"Video metadata {video_id} does not exist.")
            record.original_object_key = object_key
            ProcessingJobService.enqueue(session, video_id, bucket)
            session.flush()
            return VideoMetadataService.serialize(record)

    @staticmethod
    def serialize(record: VideoMetadataRecord) -> dict:
        return {column.name: getattr(record, column.name) for column in record.__table__.columns}

    @staticmethod
    def delete(video_id: UUID) -> None:
        with SessionLocal.begin() as session:
            record = session.get(VideoMetadataRecord, video_id)
            if record is not None:
                session.delete(record)
