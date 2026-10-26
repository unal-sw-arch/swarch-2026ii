from pydantic import BaseModel, ConfigDict, Field, field_validator


class VideoMetadata(BaseModel):
    """User-supplied fields; ID, state and blob paths are assigned by the server."""
    model_config = ConfigDict(extra="forbid")

    title: str = Field(..., max_length=255, description="Title of the video")
    description: str | None = Field(None, description="Optional description")
    category: str | None = Field(None, max_length=100, description="Optional category")

    @field_validator("title")
    @classmethod
    def reject_blank_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Must not be blank")
        return value.strip()

    @field_validator("description", "category")
    @classmethod
    def normalize_optional_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return value.strip() or None
