"""Durable local journal and per-video inter-process exclusion for the local MVP."""

from contextlib import closing, contextmanager
import json
from pathlib import Path
import sqlite3

from filelock import FileLock, Timeout

from app.video_errors import VideoError


class VideoStateStore:
    def __init__(self, root: Path, lock_timeout: float = 5.0, namespace: str = "video"):
        if namespace not in {"video", "user"}:
            raise ValueError("Unsupported state namespace")
        self.root = Path(root)
        self.namespace = namespace
        self.table = f"{namespace}_state"
        self.key_column = f"{namespace}_id"
        self.database = self.root / f"{namespace}_state.sqlite3"
        self.lock_timeout = lock_timeout

    def initialize(self) -> None:
        try:
            self.root.mkdir(parents=True, exist_ok=True)
            (self.root / "locks").mkdir(exist_ok=True)
            with FileLock(str(self.root / "schema.lock"), timeout=self.lock_timeout):
                with closing(sqlite3.connect(self.database, timeout=5)) as connection, connection:
                    version = connection.execute("PRAGMA user_version").fetchone()[0]
                    if version not in {0, 1}:
                        raise ValueError("Unsupported state schema version")
                    connection.execute("PRAGMA journal_mode=WAL")
                    connection.execute("PRAGMA synchronous=FULL")
                    connection.execute(f"CREATE TABLE IF NOT EXISTS {self.table} ({self.key_column} TEXT PRIMARY KEY, document TEXT NOT NULL)")
                    columns = [row[1] for row in connection.execute(f"PRAGMA table_info({self.table})")]
                    if columns != [self.key_column, "document"]:
                        raise ValueError("Incompatible state schema")
                    connection.execute("PRAGMA user_version=1")
            self.check()
        except (OSError, sqlite3.Error, ValueError, Timeout) as error:
            raise VideoError("STATE_UNAVAILABLE", "Could not initialize persistent video state") from error

    @contextmanager
    def _connection(self):
        connection = None
        try:
            connection = sqlite3.connect(self.database.resolve().as_uri() + "?mode=rw", uri=True, timeout=5)
            if connection.execute("PRAGMA user_version").fetchone()[0] != 1:
                raise ValueError("Unsupported state schema version")
            if [row[1] for row in connection.execute(f"PRAGMA table_info({self.table})")] != [self.key_column, "document"]:
                raise ValueError("Incompatible state schema")
            connection.execute("PRAGMA synchronous=FULL")
            with connection:
                yield connection
        except (OSError, sqlite3.Error, ValueError) as error:
            raise VideoError("STATE_UNAVAILABLE", "Could not read or confirm persistent video state") from error
        finally:
            if connection is not None:
                connection.close()

    def check(self) -> None:
        with self._connection() as connection:
            connection.execute(f"SELECT {self.key_column} FROM {self.table} LIMIT 1").fetchone()

    def read(self, video_id: str) -> dict | None:
        with self._connection() as connection:
            row = connection.execute(f"SELECT document FROM {self.table} WHERE {self.key_column} = ?", (video_id,)).fetchone()
            if row is None:
                return None
            document = json.loads(row[0])
            if not isinstance(document, dict):
                raise ValueError("Invalid state document")
            return document

    def write(self, video_id: str, document: dict) -> None:
        encoded = json.dumps(document, ensure_ascii=False, allow_nan=False)
        with self._connection() as connection:
            connection.execute(
                f"INSERT INTO {self.table}({self.key_column}, document) VALUES (?, ?) "
                f"ON CONFLICT({self.key_column}) DO UPDATE SET document=excluded.document",
                (video_id, encoded),
            )

    @contextmanager
    def video_lock(self, video_id: str):
        # IDs originate from validated UUIDs, never arbitrary paths.
        try:
            lock_name = video_id if self.namespace == "video" else f"user-{video_id}"
            with FileLock(str(self.root / "locks" / f"{lock_name}.lock"), timeout=self.lock_timeout):
                yield
        except Timeout as error:
            raise VideoError("VIDEO_BUSY", "Another request is updating this video", 429) from error
        except OSError as error:
            raise VideoError("STATE_UNAVAILABLE", "Could not acquire the video state lock") from error


class UserStateStore(VideoStateStore):
    """Separate profile journal, sharing the same durable local volume."""

    def __init__(self, root: Path, lock_timeout: float = 5.0):
        super().__init__(root, lock_timeout, namespace="user")
