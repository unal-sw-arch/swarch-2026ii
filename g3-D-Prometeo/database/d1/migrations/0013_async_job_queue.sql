-- Durable queue backing the asynchronous job processor.
--
-- Side effects that used to run synchronously on the mutation path (notably the
-- metrics-gateway writes) are now enqueued here as jobs. The RestaurantCoordinator
-- drains this table from a Durable Object alarm, retrying failures with backoff,
-- so a slow or unreachable downstream never blocks (or fails) a user mutation.
--
-- D1 stays the source of truth: a job row is only created after its originating
-- D1 write has committed, and `dedupe_key` makes replays idempotent.

CREATE TABLE async_jobs (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  -- Logical job type, e.g. 'metrics.apply' or 'metrics.rebuild'.
  kind          TEXT    NOT NULL,
  -- JSON-encoded job payload consumed by the processor for this `kind`.
  payload       TEXT    NOT NULL,
  -- 'pending' (ready/awaiting retry), 'done', or 'dead' (gave up after retries).
  status        TEXT    NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'done', 'dead')),
  -- Number of execution attempts already made.
  attempts      INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  -- Earliest epoch-millis at which the job may run next (backoff gate).
  run_after     INTEGER NOT NULL DEFAULT 0,
  -- Dedupe/idempotency key (the coordinator's idempotency-key). A given key +
  -- kind is only enqueued once, so retries of the same mutation never pile up.
  dedupe_key    TEXT    NOT NULL,
  -- Last failure message, for observability.
  last_error    TEXT,
  created_at    TEXT    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  updated_at    TEXT    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  UNIQUE (kind, dedupe_key)
);

-- The drain query reads pending jobs whose backoff gate has elapsed, oldest first.
CREATE INDEX async_jobs_due_idx
  ON async_jobs(status, run_after, id);
