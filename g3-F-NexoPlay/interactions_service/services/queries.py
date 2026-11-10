"""Read side: comment pages, like counters and event confirmation."""

import base64
from datetime import datetime
import json
from uuid import UUID

from pymongo import DESCENDING

from shared.services.interaction_store import InteractionStore, like_key


class InvalidCursorError(ValueError):
    pass


def encode_cursor(created_at: datetime, comment_id: str) -> str:
    raw = json.dumps({"t": created_at.isoformat(), "id": comment_id}).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def decode_cursor(cursor: str) -> tuple[datetime, str]:
    try:
        raw = base64.urlsafe_b64decode(cursor + "=" * (-len(cursor) % 4))
        data = json.loads(raw)
        created_at = datetime.fromisoformat(data["t"])
        comment_id = str(UUID(data["id"]))
    except (ValueError, KeyError, TypeError) as error:
        raise InvalidCursorError("Invalid cursor.") from error
    if created_at.tzinfo is None:
        raise InvalidCursorError("Invalid cursor.")
    return created_at, comment_id


class InteractionQueries:
    def __init__(self, store: InteractionStore):
        self.store = store

    def list_comments(self, video_id: UUID, limit: int, cursor: str | None) -> dict:
        query: dict = {"video_id": str(video_id)}
        if cursor:
            created_at, comment_id = decode_cursor(cursor)
            query["$or"] = [
                {"created_at": {"$lt": created_at}},
                {"created_at": created_at, "_id": {"$lt": comment_id}},
            ]
        docs = list(
            self.store.comments.find(query)
            .sort([("created_at", DESCENDING), ("_id", DESCENDING)])
            .limit(limit + 1)
        )
        page = docs[:limit]
        next_cursor = None
        if len(docs) > limit:
            last = page[-1]
            next_cursor = encode_cursor(last["created_at"], last["_id"])
        return {
            "items": [
                {
                    "comment_id": doc["_id"],
                    "video_id": doc["video_id"],
                    "user_id": doc["user_id"],
                    "author_name": doc.get("author_name"),
                    "text": doc["text"],
                    "created_at": doc["created_at"].isoformat(),
                }
                for doc in page
            ],
            "next_cursor": next_cursor,
        }

    def like_summary(self, video_id: UUID, user_id: UUID | None) -> dict:
        count = self.store.likes.count_documents({"video_id": str(video_id), "active": True})
        liked_by_me = None
        if user_id is not None:
            doc = self.store.likes.find_one({"_id": like_key(user_id, video_id)}, {"active": 1})
            liked_by_me = bool(doc and doc.get("active"))
        return {"video_id": str(video_id), "like_count": count, "liked_by_me": liked_by_me}

    def event_status(self, event_id: UUID, user_id: UUID) -> dict:
        doc = self.store.interactions.find_one(
            {"_id": str(event_id), "user_id": str(user_id)}, {"type": 1, "counted": 1},
        )
        if doc is None:
            return {"event_id": str(event_id), "status": "PENDING"}
        return {
            "event_id": str(event_id),
            "status": "CONFIRMED",
            "type": doc["type"],
            "counted": doc.get("counted", True),
        }
