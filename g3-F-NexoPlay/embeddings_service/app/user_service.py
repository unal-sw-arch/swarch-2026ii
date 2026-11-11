"""Weighted user profiles, partial reconciliation and durable per-user versions."""

import logging
import math

from app.inference import InferenceError, ModelSpec, normalize_vector
from app.metadata import TEXT_TEMPLATE_VERSION
from app.user_errors import UserError, profile_state_error
from app.user_repository import UserEmbeddingRepository
from app.user_schemas import MAX_SAFE_INTEGER, WEIGHTS_VERSION, UserRecalculateRequest
from app.video_errors import VideoError
from app.video_service import utc_now
from app.video_state import UserStateStore


logger = logging.getLogger(__name__)


def weighted_profile(contributions: list[tuple[int, list[float]]], dimension: int) -> list[float]:
    if not contributions or any(not math.isfinite(weight) or weight <= 0 for weight, _ in contributions):
        raise InferenceError("A profile needs positive finite weights")
    normalized = [(weight, normalize_vector(vector, dimension)) for weight, vector in contributions]
    total = math.fsum(weight for weight, _ in normalized)
    mean = [math.fsum((weight / total) * vector[i] for weight, vector in normalized) for i in range(dimension)]
    return normalize_vector(mean, dimension)


class UserEmbeddingService:
    def __init__(self, model: ModelSpec, store: UserStateStore, repository: UserEmbeddingRepository):
        self.model = model
        self.store = store
        self.repository = repository

    def recalculate(self, request: UserRecalculateRequest) -> dict:
        user_id = str(request.user_id)
        try:
            with self.store.video_lock(user_id):
                state = self.store.read(user_id) or {}
                highest = state.get("requested_version", 0)
                digest = request.snapshot_hash()
                if request.interaction_version < highest:
                    raise self._stale(state, highest)
                if request.interaction_version == highest and state.get("snapshot_hash") != digest:
                    raise UserError("SNAPSHOT_CONFLICT", "The same interaction version cannot identify different summaries", 409, False)
                changed_configuration = (state.get("requested_model_version") != self.model.version
                                         or state.get("requested_template_version") != TEXT_TEMPLATE_VERSION)
                if request.interaction_version != highest or changed_configuration:
                    state.update({"cached_sources": {}, "profile_revision": 0})
                    state.pop("pending_point", None)
                    state.pop("pending_response", None)
                state.update({"requested_version": request.interaction_version, "snapshot_hash": digest,
                              "requested_model_version": self.model.version,
                              "requested_template_version": TEXT_TEMPLATE_VERSION,
                              "weights_version": WEIGHTS_VERSION, "phase": "PROCESSING", "last_error": None,
                              "updated_at": utc_now()})
                self.store.write(user_id, state)
                try:
                    return self._recalculate(request, state)
                except VideoError as error:
                    failure = profile_state_error(error)
                    state["phase"] = "PENDING_CONFIRMATION" if state.get("phase") == "WRITING" else "FAILED"
                    state["last_error"] = {**failure.to_dict(), "at": utc_now()}
                    state["updated_at"] = utc_now()
                    try:
                        self.store.write(user_id, state)
                    except VideoError:
                        logger.exception("Could not record the last profile failure; no success was returned")
                    if failure is error:
                        raise
                    raise failure from error
        except VideoError as error:
            if isinstance(error, UserError):
                raise
            raise profile_state_error(error) from error

    @staticmethod
    def _stale(state: dict, version: int) -> UserError:
        return UserError("STALE_INTERACTION_VERSION", "A newer interaction summary was already received", 409, False,
                         current_interaction_version=version,
                         last_confirmed_interaction_version=(state.get("last_success") or {}).get("applied_interaction_version"))

    def _is_current(self, point: dict | None, request: UserRecalculateRequest) -> bool:
        return bool(point and point["payload"].get("interaction_version") == request.interaction_version
                    and point["payload"].get("snapshot_hash") == request.snapshot_hash()
                    and point["payload"].get("embedding_model_version") == self.model.version
                    and point["payload"].get("text_template_version") == TEXT_TEMPLATE_VERSION
                    and point["payload"].get("weights_version") == WEIGHTS_VERSION)

    def _recalculate(self, request: UserRecalculateRequest, state: dict) -> dict:
        user_id = str(request.user_id)
        self.repository.validate_collections()
        existing = self.repository.get(user_id)
        if existing:
            payload = existing["payload"]
            if payload["interaction_version"] > request.interaction_version:
                raise self._stale(state, payload["interaction_version"])
            if payload["interaction_version"] == request.interaction_version:
                if payload["snapshot_hash"] != request.snapshot_hash():
                    raise UserError("SNAPSHOT_CONFLICT", "Stored profile identifies a different summary", 409, False)
                if payload["profile_revision"] > state.get("profile_revision", 0):
                    raise UserError("PROFILE_STATE_UNAVAILABLE", "Profile revision is newer than the local journal; restore consistent state")

        pending = state.get("pending_point")
        if self._is_current(pending, request):
            if existing is None or existing["payload"] != pending["payload"]:
                existing = self.repository.upsert(pending)
            return self._confirm(state, existing, state["pending_response"])

        confirmed = state.get("confirmed_point")
        if self._is_current(confirmed, request):
            if existing is None or existing["payload"]["interaction_version"] < request.interaction_version:
                existing = self.repository.upsert(confirmed)
                return self._confirm(state, existing, state["last_success"])
            if existing["payload"] != confirmed["payload"]:
                raise UserError("PROFILE_STATE_UNAVAILABLE", "Stored profile does not match the confirmed journal")
            if confirmed["payload"]["status"] in {"READY", "NO_PROFILE"}:
                return self._confirm(state, existing, state["last_success"])

        sources = dict(state.get("cached_sources", {}))
        entries = {str(entry.video_id): entry for entry in request.interactions}
        missing = [video_id for video_id in entries if video_id not in sources]
        sources.update(self.repository.video_vectors(missing))
        unavailable = sorted(set(entries) - set(sources))
        if entries and not sources:
            raise UserError("VIDEO_EMBEDDINGS_NOT_READY", "None of the requested videos has a usable vector",
                            unavailable_video_ids=unavailable)
        if self._is_current(confirmed, request) and set(sources) == set(state.get("cached_sources", {})):
            # A partial retry with no newly available source must not rewrite or degrade it.
            return self._confirm(state, existing, state["last_success"])

        revision = state.get("profile_revision", 0) + 1
        if revision > MAX_SAFE_INTEGER:
            raise UserError("PROFILE_STATE_UNAVAILABLE", "Profile revision exceeded the supported range")
        if entries:
            try:
                vector = weighted_profile([(entries[video_id].weight, source["vector"]) for video_id, source in sources.items()], self.model.dimension)
            except (InferenceError, ValueError, OverflowError) as error:
                raise UserError("INVALID_PROFILE_VECTOR", "The weighted profile cannot be normalized", 422, False) from error
            status = "PARTIAL" if unavailable else "READY"
        else:
            # A payload-only point invalidates the old profile and fences delayed writes.
            # It contains no zero or synthetic vector and is not searchable by similarity.
            vector, status = {}, "NO_PROFILE"
        count = sum(entries[video_id].count for video_id in sources)
        timestamp = utc_now()
        payload = {
            "user_id": user_id, "interaction_version": request.interaction_version,
            "profile_revision": revision, "snapshot_hash": request.snapshot_hash(),
            "embedding_model_version": self.model.version, "text_template_version": TEXT_TEMPLATE_VERSION,
            "weights_version": WEIGHTS_VERSION, "updated_at": timestamp, "status": status,
            "interactions_count_used": count, "videos_requested": len(entries),
            "videos_used": len(sources), "videos_unavailable_count": len(unavailable),
        }
        point = {"id": user_id, "vector": vector, "payload": payload}
        response = {
            "contract_version": "1.0", "user_id": user_id,
            "requested_interaction_version": request.interaction_version,
            "applied_interaction_version": request.interaction_version,
            "profile_revision": revision, "status": status, "weights_version": WEIGHTS_VERSION,
            "embedding_model_version": self.model.version, "videos_requested": len(entries),
            "videos_used": len(sources), "interactions_count_used": count,
            "unavailable_video_ids": unavailable, "updated_at": timestamp,
        }
        state.update({"profile_revision": revision, "cached_sources": sources, "phase": "WRITING",
                      "pending_point": point, "pending_response": response, "updated_at": timestamp})
        self.store.write(user_id, state)
        saved = self.repository.upsert(point)
        return self._confirm(state, saved, response)

    def _confirm(self, state: dict, point: dict, response: dict) -> dict:
        confirmed = {**state, "phase": "READY", "last_error": None, "updated_at": utc_now(),
                     "last_success": response, "confirmed_point": point}
        if response["status"] != "PARTIAL":
            confirmed["cached_sources"] = {}
        confirmed.pop("pending_point", None)
        confirmed.pop("pending_response", None)
        self.store.write(point["id"], confirmed)
        state.clear()
        state.update(confirmed)
        return response

    def status(self, user_id: str) -> dict:
        try:
            state = self.store.read(user_id)
            if state is None:
                raise UserError("PROFILE_NOT_FOUND", "No summary has been registered for this user", 404, False)
            self.repository.validate_collections()
            point = self.repository.get(user_id)
            state = self.store.read(user_id) or state
            applied = point["payload"]["interaction_version"] if point else None
            if point:
                matches = applied == state["requested_version"] and point["payload"]["snapshot_hash"] == state["snapshot_hash"]
                status = point["payload"]["status"] if matches else "STALE"
            else:
                status = "MISSING" if state["phase"] == "READY" else state["phase"]
            return {
                "user_id": user_id, "status": status, "last_attempt_status": state["phase"],
                "requested_interaction_version": state["requested_version"], "applied_interaction_version": applied,
                "last_confirmed_interaction_version": (state.get("last_success") or {}).get("applied_interaction_version"),
                "has_profile": bool(point and point["payload"]["status"] != "NO_PROFILE"),
                "last_error": state.get("last_error"), "updated_at": state["updated_at"],
            }
        except VideoError as error:
            if isinstance(error, UserError):
                raise
            raise profile_state_error(error) from error
