import hashlib
import json
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator


WEIGHTS_VERSION = "interaction-weights-v1"
MAX_SAFE_INTEGER = 9_007_199_254_740_991


class VideoInteractions(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    video_id: UUID = Field(strict=False)
    # Contract field retained for compatibility; the producer counts playback starts.
    completed_views: int = Field(ge=0, le=MAX_SAFE_INTEGER)
    like_active: bool
    comments_count: int = Field(ge=0, le=MAX_SAFE_INTEGER)

    @property
    def weight(self) -> int:
        return self.completed_views + 3 * int(self.like_active) + 2 * self.comments_count

    @property
    def count(self) -> int:
        return self.completed_views + int(self.like_active) + self.comments_count


class UserRecalculateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    contract_version: Literal["1.0"]
    user_id: UUID = Field(strict=False)
    interaction_version: int = Field(gt=0, le=MAX_SAFE_INTEGER)
    weights_version: Literal["interaction-weights-v1"]
    interactions: list[VideoInteractions]

    @model_validator(mode="after")
    def validate_summary(self):
        ids = [entry.video_id for entry in self.interactions]
        if len(ids) != len(set(ids)):
            raise ValueError("Each video must appear only once in the full summary")
        if any(entry.weight <= 0 for entry in self.interactions):
            raise ValueError("Omit videos without an active contribution")
        if sum(entry.count for entry in self.interactions) > MAX_SAFE_INTEGER:
            raise ValueError("Interaction counts exceed the supported integer range")
        self.interactions.sort(key=lambda entry: str(entry.video_id))
        return self

    def snapshot_hash(self) -> str:
        content = json.dumps(self.model_dump(mode="json"), sort_keys=True, separators=(",", ":"))
        return hashlib.sha256(content.encode("utf-8")).hexdigest()


class UserRecalculateResponse(BaseModel):
    contract_version: Literal["1.0"] = "1.0"
    user_id: str
    requested_interaction_version: int
    applied_interaction_version: int
    profile_revision: int
    status: Literal["READY", "PARTIAL", "NO_PROFILE"]
    weights_version: Literal["interaction-weights-v1"]
    embedding_model_version: str
    videos_requested: int
    videos_used: int
    interactions_count_used: int
    unavailable_video_ids: list[str]
    updated_at: str
