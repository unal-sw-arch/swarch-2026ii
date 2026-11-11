"""Qdrant point writes with business-version fencing and read-back confirmation."""

import math

import httpx

from app.inference import InferenceError, normalize_vector
from app.qdrant_collections import CollectionDefinition, CollectionManager, CollectionSetupError
from app.video_errors import VideoError


class VideoEmbeddingRepository:
    def __init__(self, client: httpx.Client, definition: CollectionDefinition):
        self.client = client
        self.definition = definition

    def _request(self, method: str, path: str, **kwargs) -> dict | None:
        try:
            response = self.client.request(method, path, **kwargs)
            if response.status_code == 404 and method == "GET" and "/points/" in path:
                return None
            response.raise_for_status()
            body = response.json()
            if not isinstance(body, dict) or body.get("status") != "ok" or "result" not in body:
                raise ValueError("Invalid Qdrant response")
            return body["result"]
        except (httpx.HTTPError, ValueError) as error:
            raise VideoError("QDRANT_UNAVAILABLE", "Could not confirm the requested Qdrant operation") from error

    def validate_collection(self) -> None:
        info = self._request("GET", self.definition.path)
        try:
            if not isinstance(info, dict):
                raise VideoError("QDRANT_UNAVAILABLE", "Invalid collection response")
            CollectionManager.validate_schema(self.definition, info)
        except CollectionSetupError as error:
            raise VideoError(error.code, str(error), 503, error.retryable) from error

    def get(self, video_id: str) -> dict | None:
        point = self._request("GET", f"{self.definition.path}/points/{video_id}")
        if point is None:
            return None
        try:
            if not isinstance(point, dict):
                raise ValueError("Invalid point")
            payload = point["payload"]
            valid = (
                isinstance(payload, dict)
                and str(point["id"]) == video_id == payload.get("video_id")
                and type(payload.get("metadata_version")) is int
                and 0 < payload["metadata_version"] <= 9_007_199_254_740_991
                and payload.get("embedding_model_version") == self.definition.model_version
                and payload.get("text_template_version") == self.definition.metadata["nexoplay"]["text_template_version"]
                and payload.get("status") in {"READY", "DELETED"}
                and isinstance(payload.get("metadata_hash"), str) and len(payload["metadata_hash"]) == 64
                and isinstance(payload.get("updated_at"), str)
                and type(payload.get("input_tokens")) is int and type(payload.get("used_tokens")) is int
                and type(payload.get("truncated")) is bool
            )
            if not valid:
                raise ValueError("Invalid stored point identity or payload")
            if payload["status"] == "DELETED":
                if point.get("vector") not in ({}, None):
                    raise ValueError("An invalidated video cannot have a vector")
                return {"id": video_id, "payload": payload, "vector": {}}
            normalized = normalize_vector(point["vector"], self.definition.dimension)
            if abs(math.hypot(*point["vector"]) - 1) >= 1e-4:
                raise ValueError("Stored vector is not normalized")
            return {"id": video_id, "payload": payload, "vector": normalized}
        except (AssertionError, KeyError, TypeError, ValueError, InferenceError) as error:
            raise VideoError("UNMANAGED_VECTOR", "Existing point has incompatible identity, payload or vector; no overwrite performed", 409, False) from error

    def upsert(self, point: dict) -> dict:
        payload = point["payload"]
        # This filter is evaluated by Qdrant for EXISTING points, so even an old
        # delayed HTTP request cannot overwrite a higher metadata version.
        result = self._request(
            "PUT", f"{self.definition.path}/points",
            params={"wait": "true", "ordering": "strong"},
            json={
                "points": [point],
                "update_filter": {"must": [
                    {"key": "metadata_version", "range": {"lte": payload["metadata_version"]}},
                    {"key": "embedding_model_version", "match": {"value": payload["embedding_model_version"]}},
                    {"key": "text_template_version", "match": {"value": payload["text_template_version"]}},
                ]},
            },
        )
        if not isinstance(result, dict) or result.get("status") != "completed":
            raise VideoError("QDRANT_WRITE_UNCONFIRMED", "Qdrant did not confirm completion; retry the same version")
        saved = self.get(point["id"])
        if saved is None:
            raise VideoError("QDRANT_WRITE_UNCONFIRMED", "The point could not be read back after writing")
        actual = saved["payload"]
        if actual["metadata_version"] > payload["metadata_version"]:
            raise VideoError("STALE_METADATA_VERSION", "Qdrant already contains a newer version", 409, False, actual["metadata_version"])
        if any(actual.get(key) != value for key, value in payload.items()):
            raise VideoError("QDRANT_WRITE_UNCONFIRMED", "The written point does not match the requested payload")
        return saved
