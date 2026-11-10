// Drain loop that ties the durable queue (repository) to the pure processor.
//
// The RestaurantCoordinator alarm calls `drainDueJobs` once per fire. Each pass
// claims a batch of due jobs, runs them against their downstream, and persists
// success or a retry/dead decision. It returns the timestamp at which the next
// alarm should fire (the earliest pending backoff gate), or null when the queue
// is drained — letting the coordinator reschedule itself only while work remains.

import type { CloudBindings } from '../../types';
import {
  claimDueJobs, markJobDone, markJobFailed, nextPendingRunAt,
} from './repository';
import { decideAfterFailure, runJob } from './processor';

export type DrainResult = {
  processed: number;
  succeeded: number;
  failed: number;
  /** Epoch-millis for the next alarm, or null when nothing is pending. */
  nextRunAt: number | null;
};

/**
 * Processes one batch of due jobs. `now` is injected so tests and the alarm
 * share a single clock reading per pass.
 */
export async function drainDueJobs(
  env: Pick<CloudBindings, 'DB' | 'METRICS_GATEWAY_URL'>,
  now: number = Date.now(),
): Promise<DrainResult> {
  const jobs = await claimDueJobs(env.DB, now);
  let succeeded = 0;
  let failed = 0;
  for (const job of jobs) {
    try {
      await runJob(env, job);
      await markJobDone(env.DB, job.id);
      succeeded += 1;
    } catch (error) {
      const decision = decideAfterFailure(job.attempts, now);
      await markJobFailed(env.DB, job.id, decision, String(error));
      failed += 1;
    }
  }
  return {
    processed: jobs.length,
    succeeded,
    failed,
    nextRunAt: await nextPendingRunAt(env.DB),
  };
}
