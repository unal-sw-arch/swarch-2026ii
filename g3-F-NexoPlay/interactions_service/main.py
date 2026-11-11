"""Interactions API: accept views, likes and comments as broker events; serve the read side."""

from contextlib import asynccontextmanager
import logging
from pathlib import Path
from typing import Annotated
from uuid import UUID, uuid4

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator
from pymongo.errors import PyMongoError
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from starlette.concurrency import run_in_threadpool

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from shared.services.auth import Identity, authenticated_identity, authenticated_user, optional_user
from shared.services.database import engine
from shared.services.interaction_broker import EventPublishError, InteractionPublisher
from shared.services.interaction_events import (
    COMMENT_MAX_LENGTH, CommentPayload, EventType, InteractionEvent,
)
from shared.services.interaction_store import InteractionStore
from interactions_service.services.queries import InteractionQueries, InvalidCursorError
from interactions_service.services.video_lookup import video_is_available

logger = logging.getLogger(__name__)

store = InteractionStore()
queries = InteractionQueries(store)
publisher = InteractionPublisher()


@asynccontextmanager
async def lifespan(_: FastAPI):
    logging.basicConfig(level=logging.INFO)
    try:
        await run_in_threadpool(store.ensure_indexes)
        publisher.start()
        yield
    finally:
        publisher.close()
        store.close()
        engine.dispose()


app = FastAPI(
    title="Interactions Service",
    description="Views, likes and comments for NexoPlay videos",
    version="1.0.0",
    lifespan=lifespan,
)

CurrentUser = Annotated[UUID, Depends(authenticated_user)]
OptionalUser = Annotated[UUID | None, Depends(optional_user)]


class CommentIn(BaseModel):
    text: str = Field(max_length=COMMENT_MAX_LENGTH * 2)

    @field_validator("text")
    @classmethod
    def _clean(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Comment text must not be empty.")
        if len(value) > COMMENT_MAX_LENGTH:
            raise ValueError(f"Comment text must be at most {COMMENT_MAX_LENGTH} characters.")
        return value


def require_available_video(video_id: UUID) -> None:
    try:
        available = video_is_available(video_id)
    except SQLAlchemyError as error:
        raise HTTPException(503, "Video metadata storage is temporarily unavailable.") from error
    if not available:
        raise HTTPException(404, "Video not found.")


def publish(type: EventType, user_id: UUID, video_id: UUID,
            payload: CommentPayload | None = None) -> InteractionEvent:
    require_available_video(video_id)
    event = InteractionEvent.new(type, user_id, video_id, payload)
    try:
        publisher.publish(event)
    except EventPublishError as error:
        raise HTTPException(503, "Interactions are temporarily unavailable. Please retry.") from error
    return event


def accepted(event: InteractionEvent, **extra) -> dict:
    return {"event_id": str(event.event_id), "status": "PENDING", **extra}


def mongo_unavailable(error: PyMongoError) -> HTTPException:
    logger.warning("MongoDB unavailable: %r", error)
    return HTTPException(503, "Interaction storage is temporarily unavailable.")


@app.get("/health")
def health():
    checks = {"database": "ok", "interactions_db": "ok", "broker": "ok"}
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except SQLAlchemyError:
        checks["database"] = "unavailable"
    try:
        store.ping()
    except PyMongoError:
        checks["interactions_db"] = "unavailable"
    if not publisher.check():
        checks["broker"] = "unavailable"
    ready = all(value == "ok" for value in checks.values())
    return JSONResponse(
        status_code=200 if ready else 503,
        content={"status": "ok" if ready else "unavailable", **checks},
    )


@app.post("/videos/{video_id}/views", status_code=status.HTTP_202_ACCEPTED)
def record_view(video_id: UUID, user_id: CurrentUser) -> dict:
    return accepted(publish("VIEW", user_id, video_id))


@app.put("/videos/{video_id}/like", status_code=status.HTTP_202_ACCEPTED)
def like(video_id: UUID, user_id: CurrentUser) -> dict:
    return accepted(publish("LIKE", user_id, video_id), liked=True)


@app.delete("/videos/{video_id}/like", status_code=status.HTTP_202_ACCEPTED)
def unlike(video_id: UUID, user_id: CurrentUser) -> dict:
    return accepted(publish("UNLIKE", user_id, video_id), liked=False)


@app.post("/videos/{video_id}/comments", status_code=status.HTTP_202_ACCEPTED)
def create_comment(
    video_id: UUID, body: CommentIn,
    identity: Annotated[Identity, Depends(authenticated_identity)],
) -> dict:
    payload = CommentPayload(comment_id=uuid4(), text=body.text, author_name=identity.name)
    event = publish("COMMENT", identity.user_id, video_id, payload)
    return accepted(
        event,
        comment={
            "comment_id": str(payload.comment_id),
            "video_id": str(video_id),
            "user_id": str(identity.user_id),
            "author_name": payload.author_name,
            "text": payload.text,
            "created_at": event.occurred_at.isoformat(),
        },
    )


@app.get("/videos/{video_id}/comments")
def list_comments(
    video_id: UUID,
    limit: Annotated[int, Query(ge=1, le=50)] = 20,
    cursor: Annotated[str | None, Query(max_length=512)] = None,
) -> dict:
    try:
        return queries.list_comments(video_id, limit, cursor)
    except InvalidCursorError as error:
        raise HTTPException(400, str(error)) from error
    except PyMongoError as error:
        raise mongo_unavailable(error) from error


@app.get("/videos/{video_id}/likes")
def like_summary(video_id: UUID, user_id: OptionalUser) -> dict:
    try:
        return queries.like_summary(video_id, user_id)
    except PyMongoError as error:
        raise mongo_unavailable(error) from error


@app.get("/events/{event_id}")
def event_status(event_id: UUID, user_id: CurrentUser) -> dict:
    try:
        return queries.event_status(event_id, user_id)
    except PyMongoError as error:
        raise mongo_unavailable(error) from error
