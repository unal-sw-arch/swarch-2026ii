"""Interaction event contract shared by the Interactions Service and its worker."""

from datetime import datetime, timezone
import json
from typing import Literal
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator

SCHEMA_VERSION = 1
COMMENT_MAX_LENGTH = 1000
EventType = Literal["VIEW", "LIKE", "UNLIKE", "COMMENT"]


class CommentPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    comment_id: UUID
    text: str = Field(min_length=1, max_length=COMMENT_MAX_LENGTH)
    # Author's display name at the time of commenting (optional for old events).
    author_name: str | None = Field(default=None, max_length=100)


class InteractionEvent(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal[1] = SCHEMA_VERSION
    event_id: UUID
    type: EventType
    user_id: UUID
    video_id: UUID
    occurred_at: datetime
    payload: CommentPayload | None = None

    @field_validator("occurred_at")
    @classmethod
    def _utc(cls, value: datetime) -> datetime:
        if value.tzinfo is None:
            raise ValueError("occurred_at must include a timezone")
        return value.astimezone(timezone.utc)

    @model_validator(mode="after")
    def _payload_matches_type(self):
        if (self.type == "COMMENT") != (self.payload is not None):
            raise ValueError("payload is required for COMMENT events and forbidden otherwise")
        return self

    @classmethod
    def new(cls, type: EventType, user_id: UUID, video_id: UUID,
            payload: CommentPayload | None = None) -> "InteractionEvent":
        return cls(
            event_id=uuid4(), type=type, user_id=user_id, video_id=video_id,
            occurred_at=datetime.now(timezone.utc), payload=payload,
        )

    def to_bytes(self) -> bytes:
        return self.model_dump_json(exclude_none=True).encode("utf-8")

    @classmethod
    def from_bytes(cls, body: bytes) -> "InteractionEvent":
        """Raise ValueError for anything that is not a valid event."""
        try:
            return cls.model_validate(json.loads(body))
        except ValidationError as error:
            fields = ", ".join(".".join(map(str, e["loc"])) or "event" for e in error.errors())
            raise ValueError(f"Malformed interaction event (invalid: {fields})") from error
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ValueError("Malformed interaction event (not valid JSON)") from error
