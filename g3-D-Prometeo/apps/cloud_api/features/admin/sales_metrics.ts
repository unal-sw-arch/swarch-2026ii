import { rows } from '../../shared/http';
import type { MetricOperation, MetricSnapshot } from '../../infrastructure/metrics/client';

type SaleItem = {
  productId: number;
  productName: string;
  categoryId: number;
  categoryName: string;
  unitValue: number;
  quantity: number;
};

export type MetricPeriod = 'minute' | 'hour' | 'day' | 'month' | 'year';

export type MetricKeys = Record<MetricPeriod, string>;

export function bogotaMetricKeys(value: Date): MetricKeys {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(value);
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? '';
  const year = part('year');
  const month = part('month');
  const day = part('day');
  const hour = part('hour');
  const minute = part('minute');
  return {
    minute: `${year}-${month}-${day}T${hour}:${minute}`,
    hour: `${year}-${month}-${day}T${hour}`,
    day: `${year}-${month}-${day}`,
    month: `${year}-${month}`,
    year,
  };
}

// --- Incremental write operations (Mongo upserts via the gateway) -----------
// These mirror the retired D1 UPSERTs: $inc applies the delta and `clamp` pins
// the field back to >= 0, exactly like the old `MAX(0, col + delta)`.

/** Order-status counter change for one hall (e.g. +1 waiting, -1 eating). */
export function statusMetricChange(hallId: number, status: string,
  amount: number): MetricOperation {
  return {
    collection: 'hallOrderStatusTotals',
    id: `${hallId}:${status}`,
    inc: { orderCount: amount },
    setOnInsert: { hallId, status },
    clamp: ['orderCount'],
  };
}

/** Moving an order between two statuses: decrement one, increment the other. */
export function statusMetricTransition(hallId: number, previous: string,
  next: string): MetricOperation[] {
  if (previous === next) return [];
  return [
    statusMetricChange(hallId, previous, -1),
    statusMetricChange(hallId, next, 1),
  ];
}

/**
 * Operations for billing (amount = 1) or reversing (amount = -1) a closed
 * order. Reads the order's line items from D1 (still the source of truth) and
 * produces the per-hall, per-period, per-product and per-category deltas.
 */
export async function billedOrderMetricOperations(database: D1Database,
  orderId: number, hallId: number, closedAt: Date, amount = 1):
Promise<MetricOperation[]> {
  const items = await orderSaleItems(database, orderId);
  const total = items.reduce(
    (sum, item) => sum + Number(item.unitValue) * Number(item.quantity), 0,
  );
  const totalChange = total * amount;
  const now = closedAt.toISOString();
  const keys = bogotaMetricKeys(closedAt);

  const operations: MetricOperation[] = [
    {
      collection: 'hallSalesTotals',
      id: `${hallId}`,
      inc: { orderCount: amount, totalSales: totalChange },
      set: { updatedAt: now },
      setOnInsert: { hallId },
      clamp: ['orderCount', 'totalSales'],
    },
    ...Object.entries(keys).map(([period, bucketKey]): MetricOperation => ({
      collection: 'salesMetricBuckets',
      id: `${period}:${bucketKey}`,
      inc: { orderCount: amount, totalSales: totalChange },
      set: { updatedAt: now },
      setOnInsert: { period, bucketKey },
      clamp: ['orderCount', 'totalSales'],
    })),
  ];

  const products = aggregate(items, (item) => item.productId, (item) => ({
    productId: item.productId,
    name: item.productName,
    unitValue: item.unitValue,
  }));
  for (const product of products.values()) {
    operations.push({
      collection: 'dailyProductSales',
      id: `${keys.day}:${product.productId}`,
      inc: {
        quantity: product.quantity * amount,
        totalSales: product.totalSales * amount,
      },
      set: {
        productName: product.name,
        unitValue: product.unitValue,
        updatedAt: now,
      },
      setOnInsert: { dayKey: keys.day, productId: product.productId },
      clamp: ['quantity', 'totalSales'],
    });
  }

  const categories = aggregate(items, (item) => item.categoryId, (item) => ({
    categoryId: item.categoryId,
    name: item.categoryName,
  }));
  for (const category of categories.values()) {
    operations.push({
      collection: 'dailyCategorySales',
      id: `${keys.day}:${category.categoryId}`,
      inc: {
        quantity: category.quantity * amount,
        totalSales: category.totalSales * amount,
      },
      set: { categoryName: category.name, updatedAt: now },
      setOnInsert: { dayKey: keys.day, categoryId: category.categoryId },
      clamp: ['quantity', 'totalSales'],
    });
  }
  return operations;
}

async function orderSaleItems(database: D1Database, orderId: number) {
  return rows<SaleItem>(database.prepare(
    `SELECT oi.product_id AS productId, p.name AS productName,
            p.category_id AS categoryId, category.name AS categoryName,
            COALESCE(oi.unit_value, p.value) AS unitValue, oi.quantity
     FROM order_items oi
     JOIN products p ON p.id = oi.product_id
     JOIN menu_categories category ON category.id = p.category_id
     WHERE oi.order_id = ?`,
  ).bind(orderId));
}

function aggregate<T extends { unitValue: number; quantity: number }, K,
  B extends Record<string, unknown>>(items: T[], keyOf: (item: T) => K,
  base: (item: T) => B) {
  const result = new Map<K, B & { quantity: number; totalSales: number }>();
  for (const item of items) {
    const key = keyOf(item);
    const current = result.get(key) ?? {
      ...base(item), quantity: 0, totalSales: 0,
    };
    current.quantity += Number(item.quantity);
    current.totalSales += Number(item.unitValue) * Number(item.quantity);
    result.set(key, current);
  }
  return result;
}

// --- Full rebuild (resync safety net) ---------------------------------------
// Recomputes every metric collection from the closed orders still living in D1,
// then returns a MetricSnapshot the gateway writes wholesale. This replaces the
// old SQL `DELETE + INSERT ... SELECT` rebuild, which can no longer run in D1
// now that the metric tables are gone.

type RebuildRow = {
  orderId: number;
  hallId: number | null;
  closedAt: string;
  productId: number;
  productName: string;
  categoryId: number;
  categoryName: string;
  unitValue: number;
  quantity: number;
};

type StatusRow = { hallId: number | null; status: string; orderCount: number };

/** Snapshot of hall_order_status_totals recomputed from all current orders. */
export async function rebuildStatusSnapshot(database: D1Database):
Promise<Pick<MetricSnapshot, 'hallOrderStatusTotals'>> {
  const statusRows = await rows<StatusRow>(database.prepare(
    `SELECT COALESCE(o.hall_id, t.hall_id) AS hallId, o.status AS status,
            COUNT(*) AS orderCount
     FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
     GROUP BY COALESCE(o.hall_id, t.hall_id), o.status`,
  ));
  return {
    hallOrderStatusTotals: statusRows
      .filter((row) => row.hallId !== null)
      .map((row) => ({
        _id: `${row.hallId}:${row.status}`,
        hallId: Number(row.hallId),
        status: row.status,
        orderCount: Number(row.orderCount),
      })),
  };
}

/**
 * Full snapshot of all five collections recomputed from closed orders in D1.
 * Mirrors the Bogota (UTC-5) bucketing of the incremental write path.
 */
export async function rebuildMetricSnapshot(database: D1Database):
Promise<MetricSnapshot> {
  const { hallOrderStatusTotals } = await rebuildStatusSnapshot(database);
  const lineItems = await rows<RebuildRow>(database.prepare(
    `SELECT o.id AS orderId, COALESCE(o.hall_id, t.hall_id) AS hallId,
            o.updated_at AS closedAt,
            oi.product_id AS productId, p.name AS productName,
            p.category_id AS categoryId, category.name AS categoryName,
            COALESCE(oi.unit_value, p.value) AS unitValue, oi.quantity AS quantity
     FROM orders o
     LEFT JOIN hall_tables t ON t.id = o.table_id
     JOIN order_items oi ON oi.order_id = o.id
     JOIN products p ON p.id = oi.product_id
     JOIN menu_categories category ON category.id = p.category_id
     WHERE o.status = 'closed'`,
  ));

  // Group line items by order so per-order totals (and the order_count) are
  // computed once per order, matching the old GROUP BY o.id subqueries.
  const byOrder = new Map<number, { hallId: number | null; closedAt: string;
    items: RebuildRow[] }>();
  for (const row of lineItems) {
    const entry = byOrder.get(row.orderId) ??
      { hallId: row.hallId, closedAt: row.closedAt, items: [] };
    entry.items.push(row);
    byOrder.set(row.orderId, entry);
  }

  const hallTotals = new Map<number, { orderCount: number; totalSales: number;
    updatedAt: string }>();
  const buckets = new Map<string, { period: MetricPeriod; bucketKey: string;
    orderCount: number; totalSales: number; updatedAt: string }>();
  const productDay = new Map<string, { dayKey: string; productId: number;
    productName: string; quantity: number; totalSales: number; updatedAt: string }>();
  const categoryDay = new Map<string, { dayKey: string; categoryId: number;
    categoryName: string; quantity: number; totalSales: number; updatedAt: string }>();

  for (const order of byOrder.values()) {
    const keys = bogotaMetricKeys(new Date(order.closedAt));
    const orderTotal = order.items.reduce(
      (sum, item) => sum + Number(item.unitValue) * Number(item.quantity), 0,
    );

    if (order.hallId !== null) {
      const hall = hallTotals.get(order.hallId) ??
        { orderCount: 0, totalSales: 0, updatedAt: order.closedAt };
      hall.orderCount += 1;
      hall.totalSales += orderTotal;
      if (order.closedAt > hall.updatedAt) hall.updatedAt = order.closedAt;
      hallTotals.set(order.hallId, hall);
    }

    for (const [period, bucketKey] of Object.entries(keys) as [MetricPeriod, string][]) {
      const compositeKey = `${period}:${bucketKey}`;
      const bucket = buckets.get(compositeKey) ??
        { period, bucketKey, orderCount: 0, totalSales: 0, updatedAt: order.closedAt };
      bucket.orderCount += 1;
      bucket.totalSales += orderTotal;
      if (order.closedAt > bucket.updatedAt) bucket.updatedAt = order.closedAt;
      buckets.set(compositeKey, bucket);
    }

    for (const item of order.items) {
      const lineTotal = Number(item.unitValue) * Number(item.quantity);
      const productKey = `${keys.day}:${item.productId}`;
      const product = productDay.get(productKey) ?? {
        dayKey: keys.day, productId: item.productId, productName: item.productName,
        quantity: 0, totalSales: 0, updatedAt: order.closedAt,
      };
      product.quantity += Number(item.quantity);
      product.totalSales += lineTotal;
      product.productName = item.productName;
      if (order.closedAt > product.updatedAt) product.updatedAt = order.closedAt;
      productDay.set(productKey, product);

      const categoryKey = `${keys.day}:${item.categoryId}`;
      const category = categoryDay.get(categoryKey) ?? {
        dayKey: keys.day, categoryId: item.categoryId, categoryName: item.categoryName,
        quantity: 0, totalSales: 0, updatedAt: order.closedAt,
      };
      category.quantity += Number(item.quantity);
      category.totalSales += lineTotal;
      category.categoryName = item.categoryName;
      if (order.closedAt > category.updatedAt) category.updatedAt = order.closedAt;
      categoryDay.set(categoryKey, category);
    }
  }

  return {
    hallOrderStatusTotals,
    hallSalesTotals: [...hallTotals.entries()].map(([hallId, hall]) => ({
      _id: `${hallId}`,
      hallId,
      orderCount: hall.orderCount,
      totalSales: hall.totalSales,
      updatedAt: hall.updatedAt,
    })),
    salesMetricBuckets: [...buckets.values()].map((bucket) => ({
      _id: `${bucket.period}:${bucket.bucketKey}`,
      period: bucket.period,
      bucketKey: bucket.bucketKey,
      orderCount: bucket.orderCount,
      totalSales: bucket.totalSales,
      updatedAt: bucket.updatedAt,
    })),
    dailyProductSales: [...productDay.values()].map((product) => ({
      _id: `${product.dayKey}:${product.productId}`,
      dayKey: product.dayKey,
      productId: product.productId,
      productName: product.productName,
      // Weighted average unit value, matching the old ROUND(SUM/ SUM) rebuild.
      unitValue: product.quantity === 0
        ? 0 : Math.round(product.totalSales / product.quantity),
      quantity: product.quantity,
      totalSales: product.totalSales,
      updatedAt: product.updatedAt,
    })),
    dailyCategorySales: [...categoryDay.values()].map((category) => ({
      _id: `${category.dayKey}:${category.categoryId}`,
      dayKey: category.dayKey,
      categoryId: category.categoryId,
      categoryName: category.categoryName,
      quantity: category.quantity,
      totalSales: category.totalSales,
      updatedAt: category.updatedAt,
    })),
  };
}
