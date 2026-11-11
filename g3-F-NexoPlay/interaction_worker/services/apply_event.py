"""Persist one interaction event. Every step is safe to repeat when RabbitMQ redelivers."""

from datetime import datetime, timezone

from pymongo.errors import DuplicateKeyError

from shared.services.interaction_events import InteractionEvent
from shared.services.interaction_store import InteractionStore, like_key


class EventApplier:
    def __init__(self, store: InteractionStore):
        self.store = store

    def apply(self, event: InteractionEvent) -> bool:
        """Return True if the event was stored now, False if it was already stored."""
        stored = self._record(event)
        if event.type in ("LIKE", "UNLIKE"):
            self._apply_like(event)
        elif event.type == "COMMENT":
            self._apply_comment(event)
        return stored

    def _record(self, event: InteractionEvent) -> bool:
        doc = {
            "_id": str(event.event_id),
            "type": event.type,
            "user_id": str(event.user_id),
            "video_id": str(event.video_id),
            "occurred_at": event.occurred_at,
            # BSON dates retain milliseconds only. Preserve microseconds and a
            # deterministic event-ID tie-break for rapid LIKE/UNLIKE changes.
            "event_order": self._event_order(event),
            "received_at": datetime.now(timezone.utc),
            # One event per playback start; event_id handles broker redelivery.
            # Distinct starts accumulate without a time window or per-video cap.
            "counted": True,
        }
        if event.payload is not None:
            doc["comment_id"] = str(event.payload.comment_id)
        try:
            self.store.interactions.insert_one(doc)
            return True
        except DuplicateKeyError:
            return False

    def _apply_like(self, event: InteractionEvent) -> None:
        # Last writer by occurred_at wins, so an UNLIKE that arrives before its
        # earlier LIKE still leaves the like inactive.
        key = like_key(event.user_id, event.video_id)
        order = self._event_order(event)
        try:
            self.store.likes.update_one(
                {"_id": key, "$or": [
                    {"event_order": {"$lt": order}},
                    {"event_order": {"$exists": False}, "updated_at": {"$lte": event.occurred_at}},
                ]},
                {"$set": {
                    "user_id": str(event.user_id),
                    "video_id": str(event.video_id),
                    "active": event.type == "LIKE",
                    "updated_at": event.occurred_at,
                    "event_id": str(event.event_id),
                    "event_order": order,
                }},
                upsert=True,
            )
        except DuplicateKeyError:
            pass  # The stored state is newer than (or is) this event.

    @staticmethod
    def _event_order(event):
        return f"{event.occurred_at.isoformat(timespec='microseconds')}|{event.event_id}"

    def _apply_comment(self, event: InteractionEvent) -> None:
        payload = event.payload
        try:
            self.store.comments.update_one(
                {"_id": str(payload.comment_id)},
                {"$setOnInsert": {
                    "video_id": str(event.video_id),
                    "user_id": str(event.user_id),
                    "author_name": payload.author_name,
                    "text": payload.text,
                    "created_at": event.occurred_at,
                    "event_id": str(event.event_id),
                }},
                upsert=True,
            )
        except DuplicateKeyError:
            pass  # Concurrent redelivery inserted it first.
