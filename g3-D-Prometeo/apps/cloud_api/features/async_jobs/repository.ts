// D1-backed persistence for the asynchronous job queue (table `async_jobs`).
//
// This is the only module that talks SQL for background work. The processor
// (processor.ts) stays pure and the RestaurantCoordinator alarm orchestrates;
// both go through these functions so the durable queue has a single owner.

import { rows } from '../../shared/http';
import type { AsyncJob, AsyncJobKind } from './processor';

/** How many due jobs a single drain pass claims at once. */
export const DRAIN_BATCH_SIZE = 20;

type AsyncJobRow = {
  id: number;
  kind: string;
  dedupe_key: string;
  attempts: number;
  payload: string;
};

/**
 * Enqueues a job. The UNIQUE (kind, dedupe_key) constraint makes this
 * idempotent: re-enqueuing the same mutation's work is a no-op, so coordinator
 * replays never create duplicate background work. Returns true when a new row
 * was inserted, false when it was deduped away.
 */
export async function enqueueJob(
  database: D1Database,
  kind: AsyncJobKind,
  dedupeKey: string,
  payload: unknown,
): Promise<boolean> {
  const result = await database.prepare(
    `INSERT INTO async_jobs (kind, payload, dedupe_key)
     VALUES (?, ?, ?)
     ON CONFLICT (kind, dedupe_key) DO NOTHING`,
  ).bind(kind, JSON.stringify(payload), dedupeKey).run();
  return (result.meta.changes ?? 0) > 0;
}

/**
 * Claims up to `limit` pending jobs whose backoff gate has elapsed, oldest
 * first, and marks them as attempted in the same batch. Returning the claimed
 * rows lets the caller run them without re-reading.
 */
export async function claimDueJobs(
  database: D1Database,
  now: number,
  limit: number = DRAIN_BATCH_SIZE,
): Promise<AsyncJob[]> {
  const due = await rows<AsyncJobRow>(database.prepare(
    `SELECT id, kind, dedupe_key, attempts, payload
     FROM async_jobs
     WHERE status = 'pending' AND run_after <= ?
     ORDER BY id
     LIMIT ?`,
  ).bind(now, limit));
  if (due.length === 0) return [];
  // Bump attempts up front so a crash mid-run still counts the try.
  const ids = due.map((row) => row.id);
  const placeholders = ids.map(() => '?').join(',');
  await database.prepare(
    `UPDATE async_jobs
     SET attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
     WHERE id IN (${placeholders})`,
  ).bind(...ids).run();
  return due.map((row) => ({
    id: row.id,
    kind: row.kind as AsyncJobKind,
    dedupeKey: row.dedupe_key,
    attempts: row.attempts + 1,
    payload: row.payload,
  }));
}

/** Marks a job as successfully completed. */
export async function markJobDone(database: D1Database, id: number): Promise<void> {
  await database.prepare(
    `UPDATE async_jobs
     SET status = 'done', last_error = NULL, updated_at = CURRENT_TIMESTAMP
     WHERE id = ?`,
  ).bind(id).run();
}

/**
 * Records a failed attempt: either reschedules the job (status stays 'pending'
 * with a future run_after) or buries it as 'dead' once retries are exhausted.
 */
export async function markJobFailed(
  database: D1Database,
  id: number,
  decision: { status: 'pending'; runAfter: number } | { status: 'dead' },
  error: string,
): Promise<void> {
  if (decision.status === 'dead') {
    await database.prepare(
      `UPDATE async_jobs
       SET status = 'dead', last_error = ?, updated_at = CURRENT_TIMESTAMP
       WHERE id = ?`,
    ).bind(error, id).run();
    return;
  }
  await database.prepare(
    `UPDATE async_jobs
     SET status = 'pending', run_after = ?, last_error = ?,
         updated_at = CURRENT_TIMESTAMP
     WHERE id = ?`,
  ).bind(decision.runAfter, error, id).run();
}

/**
 * Epoch-millis of the earliest pending job still awaiting its backoff window,
 * or null when nothing is pending. Used to schedule the next alarm.
 */
export async function nextPendingRunAt(database: D1Database): Promise<number | null> {
  const row = await database.prepare(
    `SELECT MIN(run_after) AS next FROM async_jobs WHERE status = 'pending'`,
  ).first<{ next: number | null }>();
  return row?.next ?? null;
}

/** True while any job is still pending (ready or awaiting retry). */
export async function hasPendingJobs(database: D1Database): Promise<boolean> {
  const row = await database.prepare(
    `SELECT 1 FROM async_jobs WHERE status = 'pending' LIMIT 1`,
  ).first();
  return row !== null;
}
