from contextlib import asynccontextmanager
from pathlib import Path, PurePosixPath
from typing import Annotated
from uuid import UUID

from botocore.exceptions import BotoCoreError, ClientError
from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Response, UploadFile, status
from fastapi.responses import JSONResponse, StreamingResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from starlette.concurrency import run_in_threadpool

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from shared.services.database import engine
from processing_service.services.upload_raw import UploadRawService
from processing_service.auth import authenticated_user
from processing_service.services.delete_video import DeleteVideoService, VideoUploadInProgressError
from shared.services.video_metadata_service import VideoMetadataService
from shared.services.processing_jobs import ProcessingJobService


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        await run_in_threadpool(VideoMetadataService.initialize_schema)
        await run_in_threadpool(upload_raw_service.blob_storage.ensure_bucket_exists)
        yield
    finally:
        upload_raw_service.blob_storage.close()
        engine.dispose()


app = FastAPI(
    title="Processing Service",
    description="Service for video processing",
    version="1.0.0",
    lifespan=lifespan,
)

upload_raw_service = UploadRawService()


@app.get("/health")
def health():
    checks = {"database": "ok", "blob_storage": "ok"}
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except SQLAlchemyError:
        checks["database"] = "unavailable"
    try:
        upload_raw_service.blob_storage.check_connection()
    except (BotoCoreError, ClientError):
        checks["blob_storage"] = "unavailable"
    ready = all(value == "ok" for value in checks.values())
    return JSONResponse(
        status_code=200 if ready else 503,
        content={"status": "ok" if ready else "unavailable", **checks},
    )


@app.post("/upload_video", status_code=status.HTTP_201_CREATED)
async def upload_video(
    metadata: Annotated[str, Form(..., description="Video metadata encoded as JSON")],
    video: Annotated[UploadFile, File(..., description="Video file to be uploaded")],
    user_id: Annotated[UUID | None, Header(alias="x-user-id")] = None,
) -> dict:
    if user_id is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Authenticated user required.")
    return await upload_raw_service.upload(metadata, video, user_id)

@app.delete("/videos/{video_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_video(
    video_id: UUID,
    user_id: Annotated[UUID, Depends(authenticated_user)],
) -> Response:
    try:
        DeleteVideoService(upload_raw_service.blob_storage).delete(video_id, user_id)
    except PermissionError as error:
        raise HTTPException(403, str(error)) from error
    except LookupError as error:
        raise HTTPException(404, str(error)) from error
    except VideoUploadInProgressError as error:
        raise HTTPException(409, str(error)) from error
    except (BotoCoreError, ClientError) as error:
        raise HTTPException(503, "Video storage is temporarily unavailable. Please retry deletion.") from error
    except SQLAlchemyError as error:
        raise HTTPException(503, "Video metadata storage is temporarily unavailable. Please retry deletion.") from error
    return Response(status_code=status.HTTP_204_NO_CONTENT)


def load_video(video_id: UUID) -> dict:
    try:
        result = ProcessingJobService().get(video_id)
    except SQLAlchemyError as error:
        raise HTTPException(503, "Video metadata storage is temporarily unavailable.") from error
    if result is None:
        raise HTTPException(404, "Video not found.")
    return result


@app.get("/videos/{video_id}")
def get_video(
    video_id: UUID,
    user_id: Annotated[UUID | None, Header(alias="x-user-id")] = None,
) -> dict:
    if user_id is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Authenticated user required.")
    result = load_video(video_id)
    if result["metadata"]["owner_id"] != user_id:
        raise HTTPException(404, "Video not found.")
    return result


@app.get("/videos/{video_id}/hls/{asset_path:path}")
def get_hls_asset(video_id: UUID, asset_path: str):
    video = load_video(video_id)
    hls = video["hls"]
    if hls is None:
        raise HTTPException(409, "HLS package is not ready.")
    prefix = str(PurePosixPath(hls["master_object_key"]).parent)
    key = f"{prefix}/{asset_path}"
    asset = next((a for a in hls["assets"] if a["object_key"] == key), None)
    if asset is None:
        raise HTTPException(404, "HLS asset not found.")
    return stream_blob_asset(hls["bucket"], key, asset["content_type"])


@app.get("/videos/{video_id}/thumbnails/poster.jpg")
def get_poster(video_id: UUID):
    video = load_video(video_id)
    poster = video["thumbnail"]
    if poster is None:
        raise HTTPException(409, "Poster is not ready.")
    return stream_blob_asset(poster["bucket"], poster["object_key"], "image/jpeg")


def stream_blob_asset(bucket: str, key: str, media_type: str):
    try:
        result = upload_raw_service.blob_storage.open_object(bucket, key)
    except (BotoCoreError, ClientError) as error:
        raise HTTPException(503, "Video storage is temporarily unavailable.") from error

    def content():
        try:
            yield from result["Body"].iter_chunks(chunk_size=64 * 1024)
        finally:
            result["Body"].close()

    return StreamingResponse(
        content(), media_type=media_type,
        headers={"Content-Length": str(result["ContentLength"])},
    )
