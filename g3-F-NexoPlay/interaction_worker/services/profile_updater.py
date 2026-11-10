"""Durable, versioned full summaries for Embeddings contract 1.0.

A single MongoDB aggregation feeds both the version and contributions. The exact
request is journaled before HTTP; retries and partial reconciliation reuse it.
"""

from copy import deepcopy
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
import logging
import math
import os
from urllib.parse import urlsplit
from uuid import uuid4

from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError

from shared.services.embeddings_client import EmbeddingsClient, EmbeddingsDeliveryError
from shared.services.interaction_store import InteractionStore


logger = logging.getLogger(__name__)
MAX_VERSION = 9_007_199_254_740_991
WEIGHTS_VERSION = "interaction-weights-v1"


def utcnow():
    return datetime.now(timezone.utc)


@dataclass(frozen=True)
class ProfileSettings:
    embeddings_url: str | None = "http://embeddings-service:8090"
    threshold: int = 10
    connect_timeout: float = 5.0
    request_timeout: float = 60.0
    retry_seconds: float = 2.0
    lease_seconds: float = 120.0
    sweep_seconds: float = 1.0

    def __post_init__(self):
        if type(self.threshold) is not int or self.threshold != 10:
            raise ValueError("PROFILE_UPDATE_THRESHOLD must be 10 for contract 1.0")
        values = (self.connect_timeout, self.request_timeout, self.retry_seconds, self.lease_seconds, self.sweep_seconds)
        if not all(math.isfinite(value) and value > 0 for value in values):
            raise ValueError("Profile timeouts must be finite positive numbers")
        if self.lease_seconds < self.connect_timeout + self.request_timeout + 10:
            raise ValueError("PROFILE_LEASE_SECONDS must exceed the HTTP timeout budget by at least 10 seconds")
        if self.embeddings_url:
            url = urlsplit(self.embeddings_url)
            if url.scheme not in {"http", "https"} or not url.hostname:
                raise ValueError("EMBEDDINGS_API_URL must be an HTTP or HTTPS base URL")

    @classmethod
    def from_env(cls):
        base = os.getenv("EMBEDDINGS_API_URL", os.getenv("EMBEDDINGS_URL", "http://embeddings-service:8090"))
        return cls(
            embeddings_url=base.strip().rstrip("/") or None,
            threshold=int(os.getenv("PROFILE_UPDATE_THRESHOLD", "10")),
            connect_timeout=float(os.getenv("EMBEDDINGS_CONNECT_TIMEOUT_SECONDS", "5")),
            request_timeout=float(os.getenv("EMBEDDINGS_RESPONSE_TIMEOUT_SECONDS", "60")),
            retry_seconds=float(os.getenv("PROFILE_RETRY_SECONDS", "2")),
            lease_seconds=float(os.getenv("PROFILE_LEASE_SECONDS", "120")),
            sweep_seconds=float(os.getenv("PROFILE_SWEEP_SECONDS", "1")),
        )


@dataclass
class ProfileUpdater:
    store: InteractionStore
    settings: ProfileSettings = field(default_factory=ProfileSettings.from_env)
    client: object | None = None
    _owns_client: bool = field(default=False, init=False)

    def __post_init__(self):
        if self.client is None and self.settings.embeddings_url:
            self.client = EmbeddingsClient(base_url=self.settings.embeddings_url,
                connect_timeout=self.settings.connect_timeout, response_timeout=self.settings.request_timeout)
            self._owns_client = True

    def close(self):
        if self._owns_client:
            self.client.close()

    def interaction_version(self, user_id):
        return self.store.interactions.count_documents({"user_id": user_id, "counted": True})

    def note_activity(self, user_id):
        """Persist scheduling BEFORE RabbitMQ acknowledgement; never do HTTP here."""
        version = self.interaction_version(user_id)
        if version > MAX_VERSION:
            raise ValueError("Interaction version exceeds the supported range")
        update = {"$max": {"latest_version": version}, "$setOnInsert": {"profiled_version": 0}}
        try:
            self.store.states.update_one({"_id": user_id}, update, upsert=True)
        except DuplicateKeyError:
            self.store.states.update_one({"_id": user_id}, {"$max": {"latest_version": version}})

    def _due_filter(self, now):
        return {"$and": [
            {"$or": [{"lease_until": None}, {"lease_until": {"$lte": now}}]},
            {"$or": [{"retry_after": None}, {"retry_after": {"$lte": now}}]},
            {"$or": [
                {"pending_request": {"$ne": None}},
                {"reconciliation_request": {"$ne": None}},
                {"$expr": {"$and": [
                    {"$gte": [{"$subtract": [{"$ifNull": ["$latest_version", 0]},
                                               {"$ifNull": ["$profiled_version", 0]}]}, self.settings.threshold]},
                    {"$gt": [{"$ifNull": ["$latest_version", 0]}, {"$ifNull": ["$blocked_version", 0]}]},
                ]}},
            ]},
        ]}

    @staticmethod
    def _owner(user_id, token):
        return {"_id": user_id, "lease_token": token, "lease_until": {"$gt": utcnow()}}

    def _update_owned(self, user_id, token, update):
        return self.store.states.update_one(self._owner(user_id, token), update).matched_count == 1

    def snapshot(self, user_id):
        """All facets see the same input set, including concurrent event arrivals.

        Read the immutable log rather than separately querying mutable like and
        comment projections. Equal timestamps use event_id as a stable tie-break.
        """
        result = next(iter(self.store.interactions.aggregate([
            {"$match": {"user_id": user_id, "counted": True}},
            {"$facet": {
                "version": [{"$count": "value"}],
                "views": [{"$match": {"type": "VIEW"}},
                          {"$group": {"_id": "$video_id", "value": {"$sum": 1}}}],
                "comments": [{"$match": {"type": "COMMENT"}},
                    {"$group": {"_id": {"video": "$video_id", "comment": {"$ifNull": ["$comment_id", "$_id"]}}}},
                    {"$group": {"_id": "$_id.video", "value": {"$sum": 1}}}],
                "likes": [{"$match": {"type": {"$in": ["LIKE", "UNLIKE"]}}},
                          {"$sort": {"occurred_at": 1, "event_order": 1, "_id": 1}},
                          {"$group": {"_id": "$video_id", "value": {"$last": "$type"}}}],
            }},
        ])))
        entries = {}
        for kind, key in (("views", "completed_views"), ("comments", "comments_count"), ("likes", "like_active")):
            for row in result[kind]:
                entry = entries.setdefault(row["_id"], {"video_id": row["_id"], "completed_views": 0,
                                                        "like_active": False, "comments_count": 0})
                entry[key] = row["value"] == "LIKE" if kind == "likes" else row["value"]
        version = result["version"][0]["value"] if result["version"] else 0
        if not 0 < version <= MAX_VERSION:
            raise ValueError("A profile snapshot needs a supported positive version")
        return {"contract_version": "1.0", "user_id": user_id, "interaction_version": version,
                "weights_version": WEIGHTS_VERSION, "interactions": [entry for _, entry in sorted(entries.items())
                    if entry["completed_views"] or entry["like_active"] or entry["comments_count"]]}

    def aggregate(self, user_id):
        return self.snapshot(user_id)["interactions"]

    @staticmethod
    def _validate_result(body, response):
        """Reject malformed 200s before moving the durable checkpoint."""
        invalid = EmbeddingsDeliveryError("UNCONFIRMED_PROFILE_RESPONSE", True)
        if not isinstance(response, dict):
            raise invalid
        version = body["interaction_version"]
        if (response.get("contract_version") != "1.0" or response.get("user_id") != body["user_id"]
                or type(response.get("applied_interaction_version")) is not int
                or response["applied_interaction_version"] != version
                or type(response.get("requested_interaction_version")) is not int
                or response["requested_interaction_version"] != version
                or response.get("weights_version") != WEIGHTS_VERSION
                or not isinstance(response.get("embedding_model_version"), str) or not response["embedding_model_version"]
                or not isinstance(response.get("updated_at"), str)
                or type(response.get("profile_revision")) is not int or response["profile_revision"] < 1):
            raise invalid
        entries = {entry["video_id"]: entry for entry in body["interactions"]}
        missing = response.get("unavailable_video_ids")
        if (not isinstance(missing, list) or not all(isinstance(identifier, str) for identifier in missing)
                or len(missing) != len(set(missing)) or not set(missing) <= set(entries)):
            raise invalid
        used = set(entries) - set(missing)
        count = sum(entries[key]["completed_views"] + int(entries[key]["like_active"])
                    + entries[key]["comments_count"] for key in used)
        for key, expected in (("videos_requested", len(entries)), ("videos_used", len(used)), ("interactions_count_used", count)):
            if type(response.get(key)) is not int or response[key] != expected:
                raise invalid
        status = response.get("status")
        if not ((status == "READY" and used and not missing)
                or (status == "PARTIAL" and used and missing)
                or (status == "NO_PROFILE" and not entries)):
            raise invalid
        return response

    def _delay(self, attempts):
        # Initial 2/5/15/30/60-second policy; configurable base scales the waits.
        return min(60.0, self.settings.retry_seconds * (1, 2.5, 7.5, 15, 30)[min(max(attempts - 1, 0), 4)])

    def try_recalculate(self, user_id):
        if not self.settings.embeddings_url:
            return False
        now, token = utcnow(), str(uuid4())
        state = self.store.states.find_one_and_update(
            {"_id": user_id, **self._due_filter(now)},
            {"$set": {"lease_until": now + timedelta(seconds=self.settings.lease_seconds),
                      "lease_token": token, "last_attempt_at": now}}, return_document=ReturnDocument.AFTER)
        if state is None:
            return False
        # An uncertain write must be confirmed before a newer summary replaces it.
        body = state.get("pending_request")
        if body is None:
            fresh = state.get("latest_version", 0) - state.get("profiled_version", 0) >= self.settings.threshold
            body = self.snapshot(user_id) if fresh else state["reconciliation_request"]
        body = deepcopy(body)
        if not self._update_owned(user_id, token, {"$set": {
            "pending_request": body, "lease_until": utcnow() + timedelta(seconds=self.settings.lease_seconds),
        }}):
            return False
        try:
            response = self._validate_result(body, self.client.recalculate_user(deepcopy(body)))
        except EmbeddingsDeliveryError as error:
            return self._failed(user_id, token, state, body, error)

        previous = state.get("last_response") or {}
        same_partial = (previous.get("applied_interaction_version") == body["interaction_version"]
                        and previous.get("profile_revision") == response["profile_revision"])
        attempts = state.get("reconciliation_attempts", 0) + 1 if same_partial else 1
        partial = response["status"] == "PARTIAL"
        update = {"$max": {"profiled_version": body["interaction_version"], "latest_version": body["interaction_version"]},
            "$set": {"lease_until": None, "lease_token": None, "last_error": None,
                     "last_success_at": utcnow(), "last_response": response, "attempts": 0,
                     "reconciliation_attempts": attempts if partial else 0,
                     "reconciliation_request": body if partial else None,
                     "retry_after": utcnow() + timedelta(seconds=self._delay(attempts)) if partial else None},
            "$unset": {"pending_request": "", "blocked_request": "", "blocked_version": ""}}
        return self._update_owned(user_id, token, update)

    def _failed(self, user_id, token, state, body, error):
        attempts = state.get("attempts", 0) + 1
        values = {"lease_until": None, "lease_token": None, "attempts": attempts,
                  "last_error": {"code": error.code, "status": error.status, "retryable": error.retryable,
                                 "details": error.details, "at": utcnow()}}
        update = {"$set": values}
        if error.retryable:
            delay = max(self._delay(attempts), error.retry_after or 0)
            values["retry_after"] = utcnow() + timedelta(seconds=delay)
        else:
            # Never treat a 409's received version as a successful checkpoint.
            remote = error.details.get("current_interaction_version", 0)
            remote = remote if type(remote) is int and 0 <= remote <= MAX_VERSION else 0
            values.update({"blocked_request": body, "blocked_version": max(body["interaction_version"], remote),
                           "reconciliation_request": None, "retry_after": None})
            update["$unset"] = {"pending_request": ""}
            logger.error("Profile update for %s needs correction: %s", user_id, error.code)
        self._update_owned(user_id, token, update)
        return False

    def sweep(self, batch=50):
        if not self.settings.embeddings_url:
            return 0
        due = self.store.states.find(self._due_filter(utcnow()), {"_id": 1}).sort("last_attempt_at", 1).limit(batch)
        return sum(self.try_recalculate(doc["_id"]) for doc in due)
