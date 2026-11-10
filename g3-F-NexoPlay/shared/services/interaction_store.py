"""MongoDB collections for interactions. IDs are stored as UUID strings."""

import os

from pymongo import ASCENDING, DESCENDING, MongoClient
from pymongo.write_concern import WriteConcern


def mongo_client() -> MongoClient:
    return MongoClient(
        os.getenv("MONGODB_URI", "mongodb://localhost:27017"),
        tz_aware=True,
        serverSelectionTimeoutMS=5000,
        connectTimeoutMS=5000,
    )


class InteractionStore:
    def __init__(self, client: MongoClient | None = None):
        self.client = client or mongo_client()
        db = self.client.get_database(os.getenv("MONGODB_DB", "interactions"),
                                      write_concern=WriteConcern(w=1, j=True))
        # Append-only event log; _id is the event_id, which makes inserts idempotent.
        self.interactions = db["interactions"]
        # One document per comment; _id is the comment_id.
        self.comments = db["comments"]
        # Current like state; _id is "<user_id>:<video_id>".
        self.likes = db["likes"]
        # Per-user profile recalculation bookkeeping; _id is the user_id.
        self.states = db["user_interaction_state"]

    def ensure_indexes(self) -> None:
        self.interactions.create_index([("user_id", ASCENDING), ("counted", ASCENDING)])
        self.interactions.create_index([
            ("user_id", ASCENDING), ("video_id", ASCENDING),
            ("type", ASCENDING), ("occurred_at", ASCENDING),
        ])
        self.comments.create_index([
            ("video_id", ASCENDING), ("created_at", DESCENDING), ("_id", DESCENDING),
        ])
        self.comments.create_index([("user_id", ASCENDING)])
        self.likes.create_index([("video_id", ASCENDING), ("active", ASCENDING)])
        self.likes.create_index([("user_id", ASCENDING), ("active", ASCENDING)])
        self.states.create_index([("latest_version", ASCENDING), ("profiled_version", ASCENDING)])
        self.states.create_index([("retry_after", ASCENDING), ("lease_until", ASCENDING)])

    def ping(self) -> None:
        self.client.admin.command("ping")

    def close(self) -> None:
        self.client.close()


def like_key(user_id, video_id) -> str:
    return f"{user_id}:{video_id}"
