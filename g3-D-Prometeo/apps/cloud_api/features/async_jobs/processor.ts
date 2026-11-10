// Asynchronous job processor — the pure decision core of the system's
// background-work component.
//
// The processor owns *what* should happen to a job (run it, schedule a retry
// with backoff, or bury it as dead) and *how* a job of a given kind executes.
// It deliberately knows nothing about D1 or the Durable Object: the repository
// persists queue rows and the RestaurantCoordinator alarm drives the loop, so
// this logic stays synchronous-pure where it decides and easy to unit-test.

import { metricsClient, type MetricOperation, type MetricSnapshot }
  from '../../infrastructure/metrics/client';
import type { CloudBindings } from '../../types';

/** Job kinds the processor knows how to run. */
export type AsyncJobKind = 'metrics.apply' | 'metrics.rebuild';

/** Payload shapes per job kind. Persisted as JSON in `async_jobs.payload`. */
export type AsyncJobPayloads = {
  'metrics.apply': { operations: MetricOperation[] };
  'metrics.rebuild': { snapshot: MetricSnapshot };
};

/** A queued unit of background work, as handed to the processor. */
export type AsyncJob = {
  id: number;
  kind: AsyncJobKind;
  dedupeKey: string;
  attempts: number;
  payload: unknown;
};

/** Maximum execution attempts before a job is buried as `dead`. */
export const MAX_ATTEMPTS = 5;

/** Base backoff in milliseconds; grows exponentially per attempt. */
export const BASE_BACKOFF_MS = 1_000;

/** Upper bound so backoff never drifts beyond a minute. */
export const MAX_BACKOFF_MS = 60_000;

/**
 * Backoff before the next retry of a job that has already failed `attempts`
 * times. Exponential (base * 2^(attempts-1)) and clamped to MAX_BACKOFF_MS.
 * `attempts` is 1-based: 1 -> base, 2 -> 2*base, 3 -> 4*base, ...
 */
export function backoffMs(attempts: number): number {
  const exponent = Math.max(0, attempts - 1);
  const raw = BASE_BACKOFF_MS * 2 ** exponent;
  return Math.min(raw, MAX_BACKOFF_MS);
}

/** Outcome of deciding what to do after a job attempt failed. */
export type FailureDecision =
  | { status: 'pending'; runAfter: number }
  | { status: 'dead' };

/**
 * Given the number of attempts made so far (including the one that just
 * failed) and the current time, decide whether to retry (and when) or give up.
 */
export function decideAfterFailure(attempts: number, now: number): FailureDecision {
  if (attempts >= MAX_ATTEMPTS) return { status: 'dead' };
  return { status: 'pending', runAfter: now + backoffMs(attempts) };
}

/** Parse a persisted payload into the typed shape for a given kind. */
export function parsePayload<K extends AsyncJobKind>(
  kind: K,
  raw: unknown,
): AsyncJobPayloads[K] {
  const value = typeof raw === 'string' ? JSON.parse(raw) : raw;
  if (typeof value !== 'object' || value === null) {
    throw new Error(`Invalid payload for job kind ${kind}`);
  }
  return value as AsyncJobPayloads[K];
}

/**
 * Executes a single job against its downstream (the metrics gateway). Throws on
 * failure so the caller can apply the retry/backoff decision. `dedupeKey` is
 * forwarded as the gateway mutation id, so a replayed job never double-counts.
 */
export async function runJob(
  env: Pick<CloudBindings, 'METRICS_GATEWAY_URL'>,
  job: AsyncJob,
): Promise<void> {
  const client = metricsClient(env);
  switch (job.kind) {
    case 'metrics.apply': {
      const { operations } = parsePayload('metrics.apply', job.payload);
      await client.apply(job.dedupeKey, operations);
      return;
    }
    case 'metrics.rebuild': {
      const { snapshot } = parsePayload('metrics.rebuild', job.payload);
      await client.rebuild(job.dedupeKey, snapshot);
      return;
    }
    default: {
      // Exhaustiveness guard: unknown kinds are non-retryable.
      const unknownKind: never = job.kind;
      throw new Error(`Unknown async job kind: ${String(unknownKind)}`);
    }
  }
}
