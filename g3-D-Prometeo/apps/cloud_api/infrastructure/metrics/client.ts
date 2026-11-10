// HTTP client the Worker uses to reach the metrics gateway (which owns the
// sales-metrics data in MongoDB). The Worker cannot open raw TCP to Mongo, so
// all metric reads/writes go through this fetch-based adapter.
//
// When METRICS_GATEWAY_URL is unset (e.g. unit tests, or a stack without the
// gateway), every call degrades gracefully to a no-op / empty result instead of
// throwing, so metrics never break an order mutation.

import type { CloudBindings } from '../../types';

/** One idempotent metric mutation against a single document. */
export type MetricOperation = {
  // Logical collection name (maps to a Mongo collection in the gateway).
  collection:
    | 'hallSalesTotals'
    | 'hallOrderStatusTotals'
    | 'salesMetricBuckets'
    | 'dailyProductSales'
    | 'dailyCategorySales';
  // Composite document id, e.g. `${hallId}` or `${period}:${bucketKey}`.
  id: string;
  // Fields to increment ($inc). Deltas may be negative (reversals).
  inc?: Record<string, number>;
  // Fields to overwrite ($set), e.g. names and timestamps.
  set?: Record<string, unknown>;
  // Fields written only on first insert ($setOnInsert), e.g. key components.
  setOnInsert?: Record<string, unknown>;
  // Numeric fields clamped back to 0 if a decrement drove them negative,
  // replicating the retired SQL `MAX(0, col + delta)` semantics.
  clamp?: string[];
};

/** Full recomputed snapshot used to rebuild every collection from scratch. */
export type MetricSnapshot = {
  hallSalesTotals: Record<string, unknown>[];
  hallOrderStatusTotals: Record<string, unknown>[];
  salesMetricBuckets: Record<string, unknown>[];
  dailyProductSales: Record<string, unknown>[];
  dailyCategorySales: Record<string, unknown>[];
};

export type OverviewResult = {
  summary: { sales: number; orders: number } | null;
  points: { key: string; value: number }[];
  topProduct: { name: string; value: number; quantity: number } | null;
  categories: { name: string; value: number }[];
};

export type HallStatusCount = { status: string; count: number };
export type HallTotals = { hallId: number; orderCount: number; totalSales: number };

export class MetricsClient {
  private readonly baseUrl: string | undefined;

  constructor(env: Pick<CloudBindings, 'METRICS_GATEWAY_URL'>) {
    const url = env.METRICS_GATEWAY_URL?.trim();
    this.baseUrl = url ? url.replace(/\/$/, '') : undefined;
  }

  /** True when a gateway is configured and metric calls will actually run. */
  get enabled(): boolean {
    return this.baseUrl !== undefined;
  }

  /**
   * Applies a batch of idempotent metric operations. `mutationId` dedupes
   * retries so a replayed coordinator mutation never double-counts. Returns
   * silently when no gateway is configured.
   */
  async apply(mutationId: string, operations: MetricOperation[]): Promise<void> {
    if (!this.baseUrl || operations.length === 0) return;
    await this.post('/metrics/apply', { mutationId, operations });
  }

  /** Replaces all metric collections with a freshly recomputed snapshot. */
  async rebuild(mutationId: string, snapshot: MetricSnapshot): Promise<void> {
    if (!this.baseUrl) return;
    await this.post('/metrics/rebuild', { mutationId, snapshot });
  }

  async overview(params: {
    period: string;
    todayKey: string;
    keys: string[];
  }): Promise<OverviewResult> {
    const empty: OverviewResult = {
      summary: null, points: [], topProduct: null, categories: [],
    };
    if (!this.baseUrl) return empty;
    const query = new URLSearchParams({
      period: params.period,
      todayKey: params.todayKey,
      keys: params.keys.join(','),
    });
    return (await this.get<OverviewResult>(`/metrics/overview?${query}`)) ?? empty;
  }

  async hallStatusCounts(hallId: number): Promise<HallStatusCount[]> {
    if (!this.baseUrl) return [];
    const result = await this.get<{ counts: HallStatusCount[] }>(
      `/metrics/hall-status/${hallId}`,
    );
    return result?.counts ?? [];
  }

  async hallTotals(): Promise<HallTotals[]> {
    if (!this.baseUrl) return [];
    const result = await this.get<{ totals: HallTotals[] }>('/metrics/hall-totals');
    return result?.totals ?? [];
  }

  private async post(path: string, body: unknown): Promise<void> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      throw new Error(`metrics gateway ${path} failed: ${response.status}`);
    }
  }

  private async get<T>(path: string): Promise<T | undefined> {
    const response = await fetch(`${this.baseUrl}${path}`);
    if (!response.ok) {
      throw new Error(`metrics gateway ${path} failed: ${response.status}`);
    }
    return await response.json<T>();
  }
}

/** Convenience factory mirroring the shape used across the Worker. */
export function metricsClient(env: Pick<CloudBindings, 'METRICS_GATEWAY_URL'>): MetricsClient {
  return new MetricsClient(env);
}
