"""Synchronous contract client shared by Processing and Interaction Worker.

Scheduling, event deduplication and durable retries belong to each producer.
"""

import math
import os
from uuid import UUID

import httpx


class EmbeddingsDeliveryError(RuntimeError):
    def __init__(self, code: str, retryable: bool, status: int | None = None, *, details=None, retry_after=None):
        super().__init__(code)
        self.code, self.retryable, self.status = code, retryable, status
        self.details = details or {}
        self.retry_after = retry_after


class EmbeddingsClient:
    def __init__(self, client=None, *, base_url=None, connect_timeout=None, response_timeout=None):
        self.owns_client = client is None
        connect = float(connect_timeout if connect_timeout is not None else os.getenv("EMBEDDINGS_CONNECT_TIMEOUT_SECONDS", "5"))
        response = float(response_timeout if response_timeout is not None else os.getenv("EMBEDDINGS_RESPONSE_TIMEOUT_SECONDS", "60"))
        if not all(math.isfinite(value) and value > 0 for value in (connect, response)):
            raise ValueError("Embedding delivery timeouts must be finite positive numbers")
        self.client = client or httpx.Client(
            base_url=(base_url or os.getenv("EMBEDDINGS_URL", "http://embeddings-service:8090")).rstrip("/"),
            timeout=httpx.Timeout(response, connect=connect), trust_env=False,
        )

    def close(self):
        if self.owns_client:
            self.client.close()

    def _send(self, method, path, **kwargs):
        try:
            response = self.client.request(method, path, **kwargs)
        except httpx.HTTPError as error:
            raise EmbeddingsDeliveryError("EMBEDDINGS_UNAVAILABLE", True) from error
        if response.status_code != 200:
            details = {}
            try:
                details = response.json().get("error", {})
                if not isinstance(details, dict):
                    details = {}
                code = details.get("code", "EMBEDDINGS_REJECTED")
            except (ValueError, AttributeError):
                code = "EMBEDDINGS_REJECTED"
            try:
                retry_after = float(response.headers.get("Retry-After", "0"))
                if not math.isfinite(retry_after) or retry_after <= 0:
                    retry_after = None
            except ValueError:
                retry_after = None
            raise EmbeddingsDeliveryError(str(code), response.status_code in {408, 429} or response.status_code >= 500,
                                          response.status_code, details=details, retry_after=retry_after)
        try:
            result = response.json()
            if not isinstance(result, dict) or result.get("contract_version") != "1.0":
                raise ValueError("Invalid contract response")
            return result
        except ValueError as error:
            raise EmbeddingsDeliveryError("INVALID_EMBEDDINGS_RESPONSE", True) from error

    @staticmethod
    def _check(result, key, identifier, version_key, version, statuses):
        if (result.get(key) != str(identifier) or type(result.get(version_key)) is not int
                or result[version_key] != version or result.get("status") not in statuses):
            raise EmbeddingsDeliveryError("UNCONFIRMED_EMBEDDINGS_RESPONSE", True)
        return result

    def video(self, snapshot):
        result = self._send("POST", "/embeddings/videos", json=snapshot)
        return self._check(result, "video_id", snapshot["video_id"], "applied_metadata_version", snapshot["metadata_version"], {"READY"})

    def invalidate_video(self, video_id, version):
        video_id = str(UUID(str(video_id)))
        result = self._send("DELETE", f"/embeddings/videos/{video_id}", params={"metadata_version": version})
        return self._check(result, "video_id", video_id, "applied_metadata_version", version, {"DELETED"})

    def recalculate_user(self, snapshot):
        result = self._send("POST", "/embeddings/users/recalculate", json=snapshot)
        result = self._check(result, "user_id", snapshot["user_id"], "applied_interaction_version",
                             snapshot["interaction_version"], {"READY", "PARTIAL", "NO_PROFILE"})
        if (result.get("requested_interaction_version") != snapshot["interaction_version"]
                or result.get("weights_version") != snapshot["weights_version"]):
            raise EmbeddingsDeliveryError("UNCONFIRMED_EMBEDDINGS_RESPONSE", True)
        return result
