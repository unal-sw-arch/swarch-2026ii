import json
import logging
import os
from pathlib import PurePosixPath
from uuid import UUID

from boto3.exceptions import S3UploadFailedError
from botocore.exceptions import BotoCoreError, ClientError
from fastapi import HTTPException, UploadFile, status
from pydantic import ValidationError
from sqlalchemy.exc import SQLAlchemyError
from starlette.concurrency import run_in_threadpool

from shared.dto.video_metadata import VideoMetadata
from shared.services.blob_storage import BlobStorageService
from shared.services.video_metadata_service import VideoMetadataService

logger = logging.getLogger(__name__)


class UploadRawService:
    """Validates incoming videos and stores their original file in MinIO."""

    _ALLOWED_EXTENSIONS = {".mp4", ".avi", ".mov"}

    def __init__(self) -> None:
        self._metadata_service = VideoMetadataService()
        self.blob_storage = BlobStorageService()

    async def upload(self, raw_metadata: str, video: UploadFile, owner_id: UUID) -> dict:
        try:
            metadata = self._parse_and_validate_metadata(raw_metadata)
            filename = self._validate_video(video)
            video.file.seek(0, os.SEEK_END)
            size_bytes = video.file.tell()
            if size_bytes == 0:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                    detail="Video file must not be empty.",
                )
            video.file.seek(0)
            return await self._store(metadata, filename, video, size_bytes, owner_id)
        finally:
            await video.close()

    async def _store(
        self,
        metadata: VideoMetadata,
        filename: str,
        video: UploadFile,
        size_bytes: int,
        owner_id: UUID,
    ) -> dict:
        try:
            video_id = await run_in_threadpool(
                self._metadata_service.create,
                metadata,
                owner_id,
                filename,
                video.content_type,
                size_bytes,
            )
        except SQLAlchemyError as error:
            logger.exception("Could not create video metadata")
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Video metadata storage is temporarily unavailable.",
            ) from error

        extension = PurePosixPath(filename).suffix.lower()
        object_key = f"videos/{video_id}/original/source{extension}"
        try:
            await run_in_threadpool(
                self.blob_storage.upload,
                video.file,
                object_key,
                video.content_type,
            )
        except (BotoCoreError, ClientError, S3UploadFailedError) as error:
            logger.exception("Could not upload video %s to blob storage", video_id)
            await run_in_threadpool(self._delete_object, object_key)
            await self._delete_metadata(video_id)
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Video storage is temporarily unavailable.",
            ) from error

        try:
            stored_metadata = await run_in_threadpool(
                self._metadata_service.set_storage_location,
                video_id,
                self.blob_storage.bucket_name,
                object_key,
            )
        except (LookupError, SQLAlchemyError) as error:
            logger.exception("Could not save blob location for video %s", video_id)
            await run_in_threadpool(self._delete_object, object_key)
            await self._delete_metadata(video_id)
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Video metadata storage is temporarily unavailable.",
            ) from error

        return {
            "message": "Video uploaded successfully.",
            "video_id": video_id,
            "bucket": self.blob_storage.bucket_name,
            "object_key": object_key,
            "processing": {"job_id": video_id, "status": "PENDING"},
            "metadata": stored_metadata,
        }

    async def _delete_metadata(self, video_id: UUID) -> None:
        try:
            await run_in_threadpool(self._metadata_service.delete, video_id)
        except SQLAlchemyError:
            logger.exception("Could not clean up metadata for video %s", video_id)

    def _delete_object(self, object_key: str) -> None:
        try:
            self.blob_storage.delete(object_key)
        except (BotoCoreError, ClientError):
            logger.exception("Could not clean up blob %s", object_key)

    def _parse_and_validate_metadata(self, raw_metadata: str) -> VideoMetadata:
        try:
            metadata = VideoMetadata(**json.loads(raw_metadata))
        except (json.JSONDecodeError, ValidationError, TypeError) as error:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="Metadata must be a valid VideoMetadata JSON object.",
            ) from error

        return metadata

    def _validate_video(self, video: UploadFile) -> str:
        filename = PurePosixPath((video.filename or "").replace("\\", "/")).name
        extension = os.path.splitext(filename)[1].lower()
        if len(filename) > 255:
            raise HTTPException(422, "Video filename must contain at most 255 characters.")

        if extension not in self._ALLOWED_EXTENSIONS:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="Invalid video format. Only .mp4, .avi, and .mov are allowed.",
            )

        if not video.content_type or not video.content_type.startswith("video/"):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="Uploaded file is not a valid video.",
            )

        if len(video.content_type) > 100:
            raise HTTPException(422, "Video content type must contain at most 100 characters.")

        return filename
