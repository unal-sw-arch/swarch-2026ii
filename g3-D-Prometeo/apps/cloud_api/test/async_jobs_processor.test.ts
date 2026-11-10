import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  BASE_BACKOFF_MS, MAX_ATTEMPTS, MAX_BACKOFF_MS,
  backoffMs, decideAfterFailure, parsePayload, runJob, type AsyncJob,
} from '../features/async_jobs/processor';

test('backoff grows exponentially from the base delay', () => {
  assert.equal(backoffMs(1), BASE_BACKOFF_MS);
  assert.equal(backoffMs(2), BASE_BACKOFF_MS * 2);
  assert.equal(backoffMs(3), BASE_BACKOFF_MS * 4);
  assert.equal(backoffMs(4), BASE_BACKOFF_MS * 8);
});

test('backoff is clamped to the maximum', () => {
  assert.equal(backoffMs(100), MAX_BACKOFF_MS);
});

test('a job under the attempt limit is rescheduled with backoff', () => {
  const now = 10_000;
  const decision = decideAfterFailure(1, now);
  assert.deepEqual(decision, { status: 'pending', runAfter: now + BASE_BACKOFF_MS });
});

test('a job at the attempt limit is buried as dead', () => {
  const decision = decideAfterFailure(MAX_ATTEMPTS, 0);
  assert.deepEqual(decision, { status: 'dead' });
});

test('parsePayload accepts both JSON strings and objects', () => {
  const fromString = parsePayload('metrics.apply', '{"operations":[]}');
  assert.deepEqual(fromString, { operations: [] });
  const fromObject = parsePayload('metrics.rebuild', { snapshot: { a: 1 } });
  assert.deepEqual(fromObject, { snapshot: { a: 1 } });
});

test('parsePayload rejects a non-object payload', () => {
  assert.throws(() => parsePayload('metrics.apply', '42'));
});

test('runJob is a no-op (does not throw) when no gateway is configured', async () => {
  const job: AsyncJob = {
    id: 1,
    kind: 'metrics.apply',
    dedupeKey: 'key-123',
    attempts: 1,
    payload: { operations: [{ collection: 'hallSalesTotals', id: '1', inc: { v: 1 } }] },
  };
  // METRICS_GATEWAY_URL unset -> MetricsClient degrades to a no-op.
  await runJob({ METRICS_GATEWAY_URL: undefined }, job);
});

test('runJob forwards the dedupe key as the gateway mutation id', async () => {
  const seen: { mutationId: string; path: string }[] = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const body = JSON.parse(String(init?.body));
    seen.push({ mutationId: body.mutationId, path: String(input) });
    return new Response(null, { status: 200 });
  }) as typeof fetch;
  try {
    await runJob({ METRICS_GATEWAY_URL: 'https://gateway.test' }, {
      id: 7,
      kind: 'metrics.apply',
      dedupeKey: 'abc-789',
      attempts: 1,
      payload: { operations: [{ collection: 'hallSalesTotals', id: '1', inc: { v: 1 } }] },
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
  assert.equal(seen.length, 1);
  assert.equal(seen[0].mutationId, 'abc-789');
  assert.match(seen[0].path, /\/metrics\/apply$/);
});
