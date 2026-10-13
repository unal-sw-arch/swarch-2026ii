import { rows } from '../../shared/http';

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

export function statusMetricChange(database: D1Database, hallId: number,
  status: string, amount: number) {
  return database.prepare(
    `INSERT INTO hall_order_status_totals(hall_id, status, order_count)
     VALUES (?, ?, MAX(?, 0))
     ON CONFLICT(hall_id, status) DO UPDATE SET
       order_count = MAX(0, hall_order_status_totals.order_count + ?)`,
  ).bind(hallId, status, amount, amount);
}

export function statusMetricTransition(database: D1Database, hallId: number,
  previous: string, next: string) {
  if (previous === next) return [];
  return [
    statusMetricChange(database, hallId, previous, -1),
    statusMetricChange(database, hallId, next, 1),
  ];
}

export async function billedOrderMetricStatements(database: D1Database,
  orderId: number, hallId: number, closedAt: Date, amount = 1) {
  const items = await rows<SaleItem>(database.prepare(
    `SELECT oi.product_id AS productId, p.name AS productName,
            p.category_id AS categoryId, category.name AS categoryName,
            COALESCE(oi.unit_value, p.value) AS unitValue, oi.quantity
     FROM order_items oi
     JOIN products p ON p.id = oi.product_id
     JOIN menu_categories category ON category.id = p.category_id
     WHERE oi.order_id = ?`,
  ).bind(orderId));
  const total = items.reduce(
    (sum, item) => sum + Number(item.unitValue) * Number(item.quantity), 0,
  );
  const totalChange = total * amount;
  const now = closedAt.toISOString();
  const keys = bogotaMetricKeys(closedAt);
  const statements: D1PreparedStatement[] = [
    database.prepare(
      `INSERT INTO hall_sales_totals(hall_id, order_count, total_sales, updated_at)
       VALUES (?, MAX(?, 0), MAX(?, 0), ?)
       ON CONFLICT(hall_id) DO UPDATE SET
         order_count = MAX(0, hall_sales_totals.order_count + ?),
         total_sales = MAX(0, hall_sales_totals.total_sales + ?),
         updated_at = excluded.updated_at`,
    ).bind(hallId, amount, totalChange, now, amount, totalChange),
    ...Object.entries(keys).map(([period, bucketKey]) => database.prepare(
      `INSERT INTO sales_metric_buckets
       (period, bucket_key, order_count, total_sales, updated_at)
       VALUES (?, ?, MAX(?, 0), MAX(?, 0), ?)
       ON CONFLICT(period, bucket_key) DO UPDATE SET
         order_count = MAX(0, sales_metric_buckets.order_count + ?),
         total_sales = MAX(0, sales_metric_buckets.total_sales + ?),
         updated_at = excluded.updated_at`,
    ).bind(period, bucketKey, amount, totalChange, now, amount, totalChange)),
  ];

  const products = aggregate(items, (item) => item.productId, (item) => ({
    productId: item.productId,
    name: item.productName,
    unitValue: item.unitValue,
  }));
  for (const product of products.values()) {
    statements.push(database.prepare(
      `INSERT INTO daily_product_sales
       (day_key, product_id, product_name, unit_value, quantity, total_sales, updated_at)
       VALUES (?, ?, ?, ?, MAX(?, 0), MAX(?, 0), ?)
       ON CONFLICT(day_key, product_id) DO UPDATE SET
         product_name = excluded.product_name,
         unit_value = excluded.unit_value,
         quantity = MAX(0, daily_product_sales.quantity + ?),
         total_sales = MAX(0, daily_product_sales.total_sales + ?),
         updated_at = excluded.updated_at`,
    ).bind(keys.day, product.productId, product.name, product.unitValue,
      product.quantity * amount, product.totalSales * amount, now,
      product.quantity * amount, product.totalSales * amount));
  }

  const categories = aggregate(items, (item) => item.categoryId, (item) => ({
    categoryId: item.categoryId,
    name: item.categoryName,
  }));
  for (const category of categories.values()) {
    statements.push(database.prepare(
      `INSERT INTO daily_category_sales
       (day_key, category_id, category_name, quantity, total_sales, updated_at)
       VALUES (?, ?, ?, MAX(?, 0), MAX(?, 0), ?)
       ON CONFLICT(day_key, category_id) DO UPDATE SET
         category_name = excluded.category_name,
         quantity = MAX(0, daily_category_sales.quantity + ?),
         total_sales = MAX(0, daily_category_sales.total_sales + ?),
         updated_at = excluded.updated_at`,
    ).bind(keys.day, category.categoryId, category.name,
      category.quantity * amount, category.totalSales * amount, now,
      category.quantity * amount, category.totalSales * amount));
  }
  return statements;
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

export function rebuildStatusMetricStatements(database: D1Database) {
  return [
    database.prepare('DELETE FROM hall_order_status_totals'),
    database.prepare(
      `INSERT INTO hall_order_status_totals(hall_id, status, order_count)
       SELECT COALESCE(o.hall_id, t.hall_id), o.status, COUNT(*)
       FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
       GROUP BY COALESCE(o.hall_id, t.hall_id), o.status`,
    ),
  ];
}

export function rebuildSalesMetricStatements(database: D1Database) {
  return [
    database.prepare('DELETE FROM hall_sales_totals'),
    database.prepare('DELETE FROM sales_metric_buckets'),
    database.prepare('DELETE FROM daily_product_sales'),
    database.prepare('DELETE FROM daily_category_sales'),
    database.prepare(
      `INSERT INTO hall_sales_totals(hall_id, order_count, total_sales, updated_at)
       SELECT totals.hall_id, COUNT(*), SUM(totals.total), MAX(totals.updated_at)
       FROM (
         SELECT o.id, COALESCE(o.hall_id, t.hall_id) AS hall_id, o.updated_at,
                COALESCE(SUM(COALESCE(oi.unit_value, p.value) * oi.quantity), 0) AS total
         FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
         LEFT JOIN order_items oi ON oi.order_id = o.id
         LEFT JOIN products p ON p.id = oi.product_id
         WHERE o.status = 'closed'
         GROUP BY o.id
       ) totals GROUP BY totals.hall_id`,
    ),
    database.prepare(periodRebuildSql()),
    database.prepare(
      `INSERT INTO daily_product_sales
       (day_key, product_id, product_name, unit_value, quantity, total_sales, updated_at)
       SELECT strftime('%Y-%m-%d', o.updated_at, '-5 hours'), p.id, p.name,
              CAST(ROUND(SUM(COALESCE(oi.unit_value, p.value) * oi.quantity) * 1.0 /
                         SUM(oi.quantity)) AS INTEGER),
              SUM(oi.quantity), SUM(COALESCE(oi.unit_value, p.value) * oi.quantity),
              MAX(o.updated_at)
       FROM orders o JOIN order_items oi ON oi.order_id = o.id
       JOIN products p ON p.id = oi.product_id
       WHERE o.status = 'closed'
       GROUP BY strftime('%Y-%m-%d', o.updated_at, '-5 hours'), p.id`,
    ),
    database.prepare(
      `INSERT INTO daily_category_sales
       (day_key, category_id, category_name, quantity, total_sales, updated_at)
       SELECT strftime('%Y-%m-%d', o.updated_at, '-5 hours'), category.id, category.name,
              SUM(oi.quantity), SUM(COALESCE(oi.unit_value, p.value) * oi.quantity),
              MAX(o.updated_at)
       FROM orders o JOIN order_items oi ON oi.order_id = o.id
       JOIN products p ON p.id = oi.product_id
       JOIN menu_categories category ON category.id = p.category_id
       WHERE o.status = 'closed'
       GROUP BY strftime('%Y-%m-%d', o.updated_at, '-5 hours'), category.id`,
    ),
  ];
}

function periodRebuildSql() {
  const totals = `SELECT o.id, o.updated_at,
      COALESCE(SUM(COALESCE(oi.unit_value, p.value) * oi.quantity), 0) AS total
    FROM orders o LEFT JOIN order_items oi ON oi.order_id = o.id
    LEFT JOIN products p ON p.id = oi.product_id
    WHERE o.status = 'closed' GROUP BY o.id`;
  return `INSERT INTO sales_metric_buckets
    (period, bucket_key, order_count, total_sales, updated_at)
    SELECT 'minute', strftime('%Y-%m-%dT%H:%M', updated_at, '-5 hours'),
           COUNT(*), SUM(total), MAX(updated_at) FROM (${totals})
    GROUP BY strftime('%Y-%m-%dT%H:%M', updated_at, '-5 hours')
    UNION ALL
    SELECT 'hour', strftime('%Y-%m-%dT%H', updated_at, '-5 hours'),
           COUNT(*), SUM(total), MAX(updated_at) FROM (${totals})
    GROUP BY strftime('%Y-%m-%dT%H', updated_at, '-5 hours')
    UNION ALL
    SELECT 'day', strftime('%Y-%m-%d', updated_at, '-5 hours'),
           COUNT(*), SUM(total), MAX(updated_at) FROM (${totals})
    GROUP BY strftime('%Y-%m-%d', updated_at, '-5 hours')
    UNION ALL
    SELECT 'month', strftime('%Y-%m', updated_at, '-5 hours'),
           COUNT(*), SUM(total), MAX(updated_at) FROM (${totals})
    GROUP BY strftime('%Y-%m', updated_at, '-5 hours')
    UNION ALL
    SELECT 'year', strftime('%Y', updated_at, '-5 hours'),
           COUNT(*), SUM(total), MAX(updated_at) FROM (${totals})
    GROUP BY strftime('%Y', updated_at, '-5 hours')`;
}
