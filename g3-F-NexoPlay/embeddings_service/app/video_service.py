"""Synchronous video generation, monotonic versions and recoverable writes."""

from datetime import datetime, timezone
import hashlib
import logging

from app.inference import InferenceError, ModelUnavailableError, TextEmbeddingEngine, normalize_vector
from app.metadata import TEXT_TEMPLATE_VERSION
from app.video_errors import VideoError
from app.video_repository import VideoEmbeddingRepository
from app.video_schemas import VideoEmbeddingRequest
from app.video_state import VideoStateStore


logger = logging.getLogger(__name__)
DELETED_HASH = hashlib.sha256(b"video-deleted-v1").hexdigest()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


class VideoEmbeddingService:
    def __init__(self, engine: TextEmbeddingEngine, store: VideoStateStore, repository: VideoEmbeddingRepository):
        self.engine = engine
        self.store = store
        self.repository = repository

    def _matches(self, point: dict, request: VideoEmbeddingRequest) -> bool:
        payload = point.get("payload", {})
        return (
            payload.get("video_id") == str(request.video_id)
            and payload.get("metadata_version") == request.metadata_version
            and payload.get("metadata_hash") == request.metadata.content_hash()
            and payload.get("embedding_model_version") == self.engine.spec.version
            and payload.get("text_template_version") == TEXT_TEMPLATE_VERSION
        )

    def _response(self, point: dict, action: str) -> dict:
        payload = point["payload"]
        return {
            "contract_version": "1.0",
            "video_id": point["id"],
            "applied_metadata_version": payload["metadata_version"],
            "status": "READY",
            "result": action,
            "updated_at": payload["updated_at"],
            "embedding_model_version": payload["embedding_model_version"],
            "text_template_version": payload["text_template_version"],
            "metadata_hash": payload["metadata_hash"],
            "dimension": self.engine.spec.dimension,
            "input_tokens": payload["input_tokens"],
            "used_tokens": payload["used_tokens"],
            "truncated": payload["truncated"],
        }

    def generate(self, request: VideoEmbeddingRequest) -> dict:
        video_id = str(request.video_id)
        with self.store.video_lock(video_id):
            state = self.store.read(video_id) or {}
            highest = state.get("requested_version", 0)
            if state.get("invalidated"):
                raise VideoError("VIDEO_DELETED", "This video UUID has been invalidated", 409, False, highest)
            if request.metadata_version < highest:
                raise VideoError("STALE_METADATA_VERSION", "A newer metadata version was already received", 409, False, highest)
            digest = request.metadata.content_hash()
            if request.metadata_version == highest and state.get("requested_hash") != digest:
                raise VideoError("METADATA_VERSION_CONFLICT", "The same metadata version cannot identify different content", 409, False, highest)
            if request.metadata_version != highest:
                state.pop("pending_point", None)
                state.pop("pending_result", None)
            state.update({
                "requested_version": request.metadata_version,
                "requested_hash": digest,
                "requested_model_version": self.engine.spec.version,
                "requested_template_version": TEXT_TEMPLATE_VERSION,
                "phase": "PROCESSING",
                "last_error": None,
                "updated_at": utc_now(),
            })
            # Reserve the highest received version before inference or network work.
            self.store.write(video_id, state)
            try:
                return self._generate(request, state)
            except ModelUnavailableError as error:
                failure = VideoError("MODEL_UNAVAILABLE", "The pinned model is unavailable", 503, False)
                self._record_failure(video_id, state, failure)
                raise failure from error
            except InferenceError as error:
                failure = VideoError("INFERENCE_FAILED", "Could not generate a valid video vector")
                self._record_failure(video_id, state, failure)
                raise failure from error
            except VideoError as error:
                self._record_failure(video_id, state, error)
                raise

    def _record_failure(self, video_id: str, state: dict, error: VideoError) -> None:
        state["phase"] = "PENDING_CONFIRMATION" if state.get("phase") == "WRITING" else "FAILED"
        state["last_error"] = {**error.to_dict(), "at": utc_now()}
        state["updated_at"] = utc_now()
        try:
            self.store.write(video_id, state)
        except VideoError:
            logger.exception("Could not record the last video attempt; no success was returned")

    def _generate(self, request: VideoEmbeddingRequest, state: dict) -> dict:
        video_id = str(request.video_id)
        self.repository.validate_collection()
        current = self.repository.get(video_id)
        if current:
            if current["payload"]["status"] == "DELETED":
                raise VideoError("VIDEO_DELETED", "This video UUID has been invalidated", 409, False,
                                 current["payload"]["metadata_version"])
            remote_version = current["payload"]["metadata_version"]
            if remote_version > request.metadata_version:
                raise VideoError("STALE_METADATA_VERSION", "Qdrant already contains a newer metadata version", 409, False, remote_version)
            if remote_version == request.metadata_version and current["payload"]["metadata_hash"] != request.metadata.content_hash():
                raise VideoError("METADATA_VERSION_CONFLICT", "Stored metadata differs for the same version", 409, False, remote_version)
            if self._matches(current, request):
                return self._confirm(state, current, "UNCHANGED")

        pending = state.get("pending_point")
        if pending and self._matches(pending, request):
            point = pending
            point["vector"] = normalize_vector(point["vector"], self.engine.spec.dimension)
            action = state.get("pending_result", "UPDATED" if current else "GENERATED")
        else:
            reused = current is not None and current["payload"]["metadata_hash"] == request.metadata.content_hash()
            if reused:
                vector = current["vector"]
                previous = current["payload"]
                tokens = {key: previous[key] for key in ["input_tokens", "used_tokens", "truncated"]}
            else:
                result = self.engine.embed(request.metadata)
                if (result.dimension != self.engine.spec.dimension
                        or result.embedding_model_version != self.engine.spec.version
                        or result.text_template_version != TEXT_TEMPLATE_VERSION
                        or result.metadata_hash != request.metadata.content_hash()):
                    raise InferenceError("Inference result does not match the requested configuration")
                vector = normalize_vector(result.vector, self.engine.spec.dimension)
                tokens = {"input_tokens": result.input_tokens, "used_tokens": result.used_tokens, "truncated": result.truncated}
            action = "UNCHANGED" if reused else "UPDATED" if current else "GENERATED"
            point = {
                "id": video_id,
                "vector": vector,
                "payload": {
                    "video_id": video_id,
                    "metadata_version": request.metadata_version,
                    "metadata_hash": request.metadata.content_hash(),
                    "category": request.metadata.category,
                    "language": request.metadata.language,
                    "embedding_model_version": self.engine.spec.version,
                    "text_template_version": TEXT_TEMPLATE_VERSION,
                    "status": "READY",
                    "updated_at": utc_now(),
                    **tokens,
                },
            }

        # Persist the exact candidate before sending. Retries can recover it after
        # a crash or lost response without generating a different vector.
        state.update({"phase": "WRITING", "pending_point": point, "pending_result": action, "updated_at": utc_now()})
        self.store.write(video_id, state)
        saved = self.repository.upsert(point)
        return self._confirm(state, saved, action)

    def _confirm(self, state: dict, point: dict, action: str) -> dict:
        response = self._response(point, action)
        confirmed = {**state, "phase": "READY", "last_success": response, "last_error": None, "updated_at": utc_now()}
        confirmed.pop("pending_point", None)
        confirmed.pop("pending_result", None)
        self.store.write(point["id"], confirmed)
        state.clear()
        state.update(confirmed)
        return response

    def status(self, video_id: str) -> dict:
        state = self.store.read(video_id)
        if state is None:
            raise VideoError("VIDEO_EMBEDDING_NOT_FOUND", "No request has been registered for this UUID", 404, False)
        self.repository.validate_collection()
        point = self.repository.get(video_id)
        # Refresh the journal after the network read in case a writer advanced it.
        state = self.store.read(video_id) or state
        phase = state["phase"]
        if point:
            payload = point["payload"]
            ready = (payload["metadata_version"] == state["requested_version"]
                     and payload["metadata_hash"] == state["requested_hash"])
            status = payload["status"] if ready else "STALE"
            applied = payload["metadata_version"]
        else:
            applied = None
            status = "MISSING" if phase == "READY" else phase
        return {
            "video_id": video_id,
            "status": status,
            "last_attempt_status": phase,
            "requested_metadata_version": state["requested_version"],
            "applied_metadata_version": applied,
            "last_confirmed_metadata_version": (state.get("last_success") or {}).get("applied_metadata_version"),
            "has_embedding": point is not None and point["payload"]["status"] == "READY",
            "last_error": state.get("last_error"),
            "updated_at": state["updated_at"],
        }

    def invalidate(self, video_id: str, version: int) -> dict:
        with self.store.video_lock(video_id):
            state = self.store.read(video_id) or {}
            highest = state.get("requested_version", 0)
            if version < highest:
                raise VideoError("STALE_METADATA_VERSION", "A newer metadata version was already received", 409, False, highest)
            if version == highest and state.get("requested_hash") != DELETED_HASH:
                raise VideoError("METADATA_VERSION_CONFLICT", "Invalidation requires a newer metadata version", 409, False, highest)
            if version != highest:
                state.pop("pending_point", None)
                state.pop("pending_result", None)
            state.update({"requested_version": version, "requested_hash": DELETED_HASH,
                          "requested_model_version": self.engine.spec.version,
                          "requested_template_version": TEXT_TEMPLATE_VERSION,
                          "invalidated": True, "phase": "PROCESSING", "last_error": None, "updated_at": utc_now()})
            self.store.write(video_id, state)
            try:
                self.repository.validate_collection()
                current = self.repository.get(video_id)
                if current and current["payload"]["metadata_version"] > version:
                    raise VideoError("STALE_METADATA_VERSION", "Qdrant contains a newer version", 409, False,
                                     current["payload"]["metadata_version"])
                if current and current["payload"]["metadata_version"] == version:
                    if current["payload"]["status"] != "DELETED" or current["payload"]["metadata_hash"] != DELETED_HASH:
                        raise VideoError("METADATA_VERSION_CONFLICT", "This version identifies existing metadata", 409, False, version)
                    return self._confirm_invalidation(state, current, "UNCHANGED")
                point = state.get("pending_point")
                if not point:
                    point = {"id": video_id, "vector": {}, "payload": {
                        "video_id": video_id, "metadata_version": version, "metadata_hash": DELETED_HASH,
                        "embedding_model_version": self.engine.spec.version, "text_template_version": TEXT_TEMPLATE_VERSION,
                        "status": "DELETED", "updated_at": utc_now(), "category": None, "language": None,
                        "input_tokens": 0, "used_tokens": 0, "truncated": False,
                    }}
                state.update({"phase": "WRITING", "pending_point": point, "updated_at": utc_now()})
                self.store.write(video_id, state)
                return self._confirm_invalidation(state, self.repository.upsert(point), "INVALIDATED")
            except VideoError as error:
                self._record_failure(video_id, state, error)
                raise

    def _confirm_invalidation(self, state: dict, point: dict, action: str) -> dict:
        payload = point["payload"]
        result = {"contract_version": "1.0", "video_id": point["id"],
                  "applied_metadata_version": payload["metadata_version"], "status": "DELETED",
                  "result": action, "updated_at": payload["updated_at"],
                  "embedding_model_version": self.engine.spec.version}
        confirmed = {**state, "phase": "READY", "last_success": result, "last_error": None, "updated_at": utc_now()}
        confirmed.pop("pending_point", None)
        confirmed.pop("pending_result", None)
        self.store.write(point["id"], confirmed)
        state.clear()
        state.update(confirmed)
        return result
