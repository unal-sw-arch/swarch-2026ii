"""Canonical metadata and deterministic, versioned input for text models."""

import hashlib
import json
import re
import unicodedata

from pydantic import BaseModel, ConfigDict, Field, field_validator


TEXT_TEMPLATE_VERSION = "video-text-v1"


def clean_text(value: str) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", value)).strip()


class VideoMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    title: str = Field(min_length=1, max_length=255)
    description: str | None = None
    category: str | None = Field(default=None, max_length=100)
    tags: list[str] = Field(default_factory=list)
    language: str | None = None

    @field_validator("title", "description", "category", "language", mode="before")
    @classmethod
    def normalize_fields(cls, value):
        if isinstance(value, str):
            return clean_text(value)
        return value

    @field_validator("description", "category", "language")
    @classmethod
    def empty_to_none(cls, value: str | None) -> str | None:
        return value or None

    @field_validator("tags")
    @classmethod
    def normalize_tags(cls, values: list[str]) -> list[str]:
        return sorted({clean_text(tag).casefold() for tag in values if clean_text(tag)})

    @field_validator("language")
    @classmethod
    def normalize_language(cls, value: str | None) -> str | None:
        return value.lower() if value else None

    def to_text(self) -> str:
        # Put identification and short descriptors before the potentially long description.
        lines = [f"Título: {self.title}"]
        if self.category:
            lines.append(f"Categoría: {self.category}")
        if self.tags:
            lines.append(f"Etiquetas: {', '.join(self.tags)}")
        if self.description:
            lines.append(f"Descripción: {self.description}")
        return "\n".join(lines)

    def content_hash(self) -> str:
        canonical = json.dumps(
            {"template": TEXT_TEMPLATE_VERSION, "metadata": self.model_dump()},
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()
