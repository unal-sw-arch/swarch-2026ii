"""Configuration for offline CPU inference and the Qdrant health probe."""

from dataclasses import dataclass
from math import isfinite
import os
from pathlib import Path
import re
from urllib.parse import urlsplit


@dataclass(frozen=True)
class Settings:
    qdrant_url: str = "http://qdrant:6333"
    qdrant_timeout_seconds: float = 5.0
    model_key: str | None = None
    model_root: Path = Path(__file__).resolve().parents[2] / "artifacts" / "embeddings" / "models"
    cpu_threads: int = 4
    video_collection: str = "video_embeddings"
    user_collection: str = "user_embeddings"
    expected_model_version: str | None = None
    qdrant_init_retry_seconds: float = 5.0
    state_root: Path = Path(__file__).resolve().parents[2] / "artifacts" / "embeddings" / "state"
    video_lock_timeout_seconds: float = 5.0
    user_lock_timeout_seconds: float = 5.0

    def __post_init__(self) -> None:
        url = urlsplit(self.qdrant_url)
        if url.scheme not in {"http", "https"} or not url.hostname:
            raise ValueError("QDRANT_URL must be an HTTP or HTTPS URL")
        if not isfinite(self.qdrant_timeout_seconds) or self.qdrant_timeout_seconds <= 0:
            raise ValueError("QDRANT_TIMEOUT_SECONDS must be a finite positive number")
        if self.cpu_threads < 1:
            raise ValueError("EMBEDDING_CPU_THREADS must be positive")
        for name in (self.video_collection, self.user_collection):
            if not re.fullmatch(r"[A-Za-z0-9_-]{1,255}", name):
                raise ValueError("Collection names must use letters, digits, underscores or hyphens")
        if self.video_collection == self.user_collection:
            raise ValueError("Video and user collections must have different names")
        if not isfinite(self.qdrant_init_retry_seconds) or self.qdrant_init_retry_seconds <= 0:
            raise ValueError("QDRANT_INIT_RETRY_SECONDS must be a finite positive number")
        if not isfinite(self.video_lock_timeout_seconds) or self.video_lock_timeout_seconds < 0:
            raise ValueError("VIDEO_LOCK_TIMEOUT_SECONDS must be a finite nonnegative number")
        if not isfinite(self.user_lock_timeout_seconds) or self.user_lock_timeout_seconds < 0:
            raise ValueError("USER_LOCK_TIMEOUT_SECONDS must be a finite nonnegative number")

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            qdrant_url=os.getenv("QDRANT_URL", "http://qdrant:6333").rstrip("/"),
            qdrant_timeout_seconds=float(os.getenv("QDRANT_TIMEOUT_SECONDS", "5")),
            model_key=os.getenv("EMBEDDING_MODEL_KEY", "").strip() or None,
            model_root=Path(os.getenv("EMBEDDING_MODEL_ROOT", str(cls.model_root))),
            cpu_threads=int(os.getenv("EMBEDDING_CPU_THREADS", "4")),
            video_collection=os.getenv("VIDEO_COLLECTION", "video_embeddings"),
            user_collection=os.getenv("USER_COLLECTION", "user_embeddings"),
            expected_model_version=os.getenv("EMBEDDING_MODEL_VERSION", "").strip() or None,
            qdrant_init_retry_seconds=float(os.getenv("QDRANT_INIT_RETRY_SECONDS", "5")),
            state_root=Path(os.getenv("EMBEDDING_STATE_ROOT", str(cls.state_root))),
            video_lock_timeout_seconds=float(os.getenv("VIDEO_LOCK_TIMEOUT_SECONDS", "5")),
            user_lock_timeout_seconds=float(os.getenv("USER_LOCK_TIMEOUT_SECONDS", "5")),
        )
