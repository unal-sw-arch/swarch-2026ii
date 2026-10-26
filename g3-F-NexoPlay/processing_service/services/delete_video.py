from uuid import UUID

from sqlalchemy import delete, select

from shared.services.database import SessionLocal
from shared.services.processing_jobs import ProcessingJob, VideoHLSRecord, VideoVariant
from shared.services.video_metadata_service import VideoMetadataRecord


class VideoUploadInProgressError(RuntimeError):
    pass


class DeleteVideoService:
    def __init__(self, blob_storage, session_factory=None):
        self.blob_storage = blob_storage
        self.sessions = session_factory or SessionLocal

    def delete(self, video_id: UUID, user_id: UUID) -> None:
        with self.sessions.begin() as session:
            # Workers and the dispatcher lock the job first. Holding this lock
            # fences artifact uploads until the job is deleted along with the video.
            job = session.scalar(
                select(ProcessingJob).where(ProcessingJob.video_id == video_id).with_for_update()
            )
            video = session.scalar(
                select(VideoMetadataRecord).where(VideoMetadataRecord.id == video_id).with_for_update()
            )
            if video is None:
                raise LookupError("Video not found.")
            if video.owner_id != user_id:
                raise PermissionError("Only the owner can delete this video.")
            if video.original_object_key is None:
                raise VideoUploadInProgressError("Wait for the upload to finish before deleting the video.")

            buckets = {job.storage_bucket if job else self.blob_storage.bucket_name}
            buckets.update(session.scalars(
                select(VideoVariant.storage_bucket).where(VideoVariant.video_id == video_id)
            ).all())
            hls = session.get(VideoHLSRecord, video_id)
            if hls:
                buckets.add(hls.manifest["bucket"])
            poster = (job.source_metadata or {}).get("poster") if job else None
            if poster:
                buckets.add(poster["bucket"])

            # Include partial/unregistered HLS outputs, not just the master file.
            # S3 deletes are idempotent; on a storage error the DB transaction
            # rolls back, so the owner can retry the same request.
            for bucket in sorted(buckets):
                self.blob_storage.delete_prefix(bucket, f"videos/{video_id}/")

            for model in (VideoVariant, VideoHLSRecord, ProcessingJob):
                session.execute(delete(model).where(model.video_id == video_id))
            session.delete(video)
