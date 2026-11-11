from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.metadata import VideoMetadata


class VideoEmbeddingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    contract_version: Literal["1.0"]
    video_id: UUID = Field(strict=False)
    metadata_version: int = Field(gt=0, le=9_007_199_254_740_991)
    metadata: VideoMetadata


class VideoEmbeddingResponse(BaseModel):
    contract_version: Literal["1.0"] = "1.0"
    video_id: str
    applied_metadata_version: int
    status: Literal["READY"] = "READY"
    result: Literal["GENERATED", "UPDATED", "UNCHANGED"]
    updated_at: str
    embedding_model_version: str
    text_template_version: str
    metadata_hash: str
    dimension: int
    input_tokens: int
    used_tokens: int
    truncated: bool
