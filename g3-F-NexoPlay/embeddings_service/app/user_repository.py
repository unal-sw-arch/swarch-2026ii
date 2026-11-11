"""Batched video retrieval and versioned, conditionally updated user points."""

from uuid import UUID

import httpx

from app.inference import InferenceError, normalize_vector
from app.qdrant_collections import CollectionDefinition, CollectionManager, CollectionSetupError
from app.user_errors import UserError
from app.user_schemas import MAX_SAFE_INTEGER, WEIGHTS_VERSION


class UserEmbeddingRepository:
    def __init__(self, client: httpx.Client, videos: CollectionDefinition, users: CollectionDefinition):
        self.client = client
        self.videos = videos
        self.users = users

    def _request(self, method: str, path: str, **kwargs):
        try:
            response = self.client.request(method, path, **kwargs)
            if method == "GET" and "/points/" in path and response.status_code == 404:
                return None
            response.raise_for_status()
            body = response.json()
            if not isinstance(body, dict) or body.get("status") != "ok" or "result" not in body:
                raise ValueError("Invalid response")
            return body["result"]
        except (httpx.HTTPError, ValueError) as error:
            raise UserError("QDRANT_UNAVAILABLE", "Could not confirm the requested profile operation") from error

    def validate_collections(self):
        for definition in (self.videos, self.users):
            info = self._request("GET", definition.path)
            if not isinstance(info, dict):
                raise UserError("QDRANT_UNAVAILABLE", "Invalid collection response")
            try:
                CollectionManager.validate_schema(definition, info)
            except CollectionSetupError as error:
                raise UserError(error.code, str(error), 503, error.retryable) from error

    def video_vectors(self, ids: list[str]) -> dict:
        sources = {}
        for offset in range(0, len(ids), 128):
            chunk = ids[offset:offset + 128]
            result = self._request("POST", f"{self.videos.path}/points", json={
                "ids": chunk, "with_payload": True, "with_vector": True,
            })
            if not isinstance(result, list):
                raise UserError("QDRANT_UNAVAILABLE", "Invalid batch of video vectors")
            seen = set()
            for point in result:
                try:
                    video_id = str(UUID(str(point["id"])))
                except (KeyError, TypeError, ValueError) as error:
                    raise UserError("QDRANT_UNAVAILABLE", "Invalid video identifier in batch response") from error
                if video_id not in chunk or video_id in seen:
                    raise UserError("QDRANT_UNAVAILABLE", "Unexpected or duplicate video in batch response")
                seen.add(video_id)
                try:
                    payload = point["payload"]
                    valid = (
                        payload.get("video_id") == video_id
                        and payload.get("status") == "READY"
                        and payload.get("embedding_model_version") == self.videos.model_version
                        and payload.get("text_template_version") == self.videos.metadata["nexoplay"]["text_template_version"]
                        and type(payload.get("metadata_version")) is int and payload["metadata_version"] > 0
                        and isinstance(payload.get("metadata_hash"), str)
                        and isinstance(point.get("vector"), list)
                    )
                    if not valid:
                        continue
                    vector = normalize_vector(point["vector"], self.videos.dimension)
                    sources[video_id] = {"vector": vector, "metadata_version": payload["metadata_version"],
                                         "metadata_hash": payload["metadata_hash"]}
                except (KeyError, TypeError, AttributeError, ValueError, InferenceError):
                    # Individual missing or incompatible vectors allow a partial profile.
                    continue
        return sources

    def get(self, user_id: str) -> dict | None:
        point = self._request("GET", f"{self.users.path}/points/{user_id}")
        if point is None:
            return None
        try:
            payload = point["payload"]
            valid = (
                isinstance(payload, dict)
                and str(point["id"]) == user_id == payload.get("user_id")
                and type(payload.get("interaction_version")) is int
                and 0 < payload["interaction_version"] <= MAX_SAFE_INTEGER
                and type(payload.get("profile_revision")) is int
                and 0 < payload["profile_revision"] <= MAX_SAFE_INTEGER
                and payload.get("embedding_model_version") == self.users.model_version
                and payload.get("text_template_version") == self.users.metadata["nexoplay"]["text_template_version"]
                and payload.get("weights_version") == WEIGHTS_VERSION
                and isinstance(payload.get("snapshot_hash"), str) and len(payload["snapshot_hash"]) == 64
                and payload.get("status") in {"READY", "PARTIAL", "NO_PROFILE"}
                and isinstance(payload.get("updated_at"), str)
            )
            if not valid:
                raise ValueError("Incompatible profile payload")
            if payload["status"] == "NO_PROFILE":
                if point.get("vector") not in ({}, None):
                    raise ValueError("A cleared profile cannot have a vector")
                vector = {}
            else:
                if not isinstance(point.get("vector"), list):
                    raise ValueError("Invalid profile vector")
                vector = normalize_vector(point["vector"], self.users.dimension)
            return {"id": user_id, "payload": payload, "vector": vector}
        except (KeyError, TypeError, ValueError, InferenceError) as error:
            raise UserError("INCOMPATIBLE_PROFILE", "The existing profile is not compatible; no overwrite performed", 409, False) from error

    def upsert(self, point: dict) -> dict:
        payload = point["payload"]
        version, revision = payload["interaction_version"], payload["profile_revision"]
        result = self._request("PUT", f"{self.users.path}/points",
            params={"wait": "true", "ordering": "strong"}, json={
                "points": [point],
                "update_filter": {"must": [
                    {"key": "embedding_model_version", "match": {"value": payload["embedding_model_version"]}},
                    {"key": "weights_version", "match": {"value": WEIGHTS_VERSION}},
                    {"key": "text_template_version", "match": {"value": payload["text_template_version"]}},
                    {"should": [
                        {"key": "interaction_version", "range": {"lt": version}},
                        {"must": [
                            {"key": "interaction_version", "match": {"value": version}},
                            {"key": "profile_revision", "range": {"lte": revision}},
                        ]},
                    ]},
                ]},
            })
        if not isinstance(result, dict) or result.get("status") != "completed":
            raise UserError("PROFILE_WRITE_UNCONFIRMED", "Profile write was not confirmed; retry the same summary")
        saved = self.get(point["id"])
        if saved is None:
            raise UserError("PROFILE_WRITE_UNCONFIRMED", "Could not read back the stored profile")
        actual = saved["payload"]
        if (actual["interaction_version"], actual["profile_revision"]) > (version, revision):
            raise UserError("STALE_INTERACTION_VERSION", "A newer profile or reconciliation already exists", 409, False,
                            current_interaction_version=actual["interaction_version"], current_profile_revision=actual["profile_revision"])
        if actual != payload:
            raise UserError("PROFILE_WRITE_UNCONFIRMED", "Stored profile does not match the requested payload")
        return saved
