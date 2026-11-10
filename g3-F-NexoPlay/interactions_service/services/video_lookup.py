from uuid import UUID

from sqlalchemy import text

from shared.services.database import engine


def video_is_available(video_id: UUID) -> bool:
    """Only processed videos can receive interactions. Raises SQLAlchemyError."""
    with engine.connect() as connection:
        status = connection.execute(
            text("SELECT status FROM video_metadata WHERE id = :id"), {"id": video_id},
        ).scalar_one_or_none()
    return status == "COMPLETED"
