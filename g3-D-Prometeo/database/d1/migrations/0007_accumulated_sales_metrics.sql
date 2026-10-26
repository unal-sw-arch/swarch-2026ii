CREATE TABLE hall_sales_totals (
  hall_id INTEGER PRIMARY KEY,
  order_count INTEGER NOT NULL DEFAULT 0 CHECK (order_count >= 0),
  total_sales INTEGER NOT NULL DEFAULT 0 CHECK (total_sales >= 0),
  updated_at TEXT NOT NULL,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE TABLE hall_order_status_totals (
  hall_id INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('waiting', 'eating', 'closed')),
  order_count INTEGER NOT NULL DEFAULT 0 CHECK (order_count >= 0),
  PRIMARY KEY (hall_id, status),
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE TABLE sales_metric_buckets (
  period TEXT NOT NULL CHECK (period IN ('hour', 'day', 'month', 'year')),
  bucket_key TEXT NOT NULL,
  order_count INTEGER NOT NULL DEFAULT 0 CHECK (order_count >= 0),
  total_sales INTEGER NOT NULL DEFAULT 0 CHECK (total_sales >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (period, bucket_key)
);

CREATE TABLE daily_product_sales (
  day_key TEXT NOT NULL,
  product_id INTEGER NOT NULL,
  product_name TEXT NOT NULL,
  unit_value INTEGER NOT NULL,
  quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  total_sales INTEGER NOT NULL DEFAULT 0 CHECK (total_sales >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (day_key, product_id)
);

CREATE INDEX daily_product_sales_ranking_idx
  ON daily_product_sales(day_key, quantity DESC);

CREATE TABLE daily_category_sales (
  day_key TEXT NOT NULL,
  category_id INTEGER NOT NULL,
  category_name TEXT NOT NULL,
  quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  total_sales INTEGER NOT NULL DEFAULT 0 CHECK (total_sales >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (day_key, category_id)
);

CREATE INDEX daily_category_sales_ranking_idx
  ON daily_category_sales(day_key, total_sales DESC);

INSERT INTO hall_order_status_totals(hall_id, status, order_count)
SELECT COALESCE(o.hall_id, t.hall_id), o.status, COUNT(*)
FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
GROUP BY COALESCE(o.hall_id, t.hall_id), o.status;

INSERT INTO hall_sales_totals(hall_id, order_count, total_sales, updated_at)
SELECT totals.hall_id, COUNT(*), SUM(totals.total), MAX(totals.updated_at)
FROM (
  SELECT o.id, COALESCE(o.hall_id, t.hall_id) AS hall_id, o.updated_at,
         COALESCE(SUM(COALESCE(oi.unit_value, p.value) * oi.quantity), 0) AS total
  FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
  LEFT JOIN order_items oi ON oi.order_id = o.id
  LEFT JOIN products p ON p.id = oi.product_id
  WHERE o.status = 'closed'
  GROUP BY o.id
) totals GROUP BY totals.hall_id;

INSERT INTO sales_metric_buckets(period, bucket_key, order_count, total_sales, updated_at)
WITH order_totals AS (
  SELECT o.id, o.updated_at,
         COALESCE(SUM(COALESCE(oi.unit_value, p.value) * oi.quantity), 0) AS total
  FROM orders o LEFT JOIN order_items oi ON oi.order_id = o.id
  LEFT JOIN products p ON p.id = oi.product_id
  WHERE o.status = 'closed' GROUP BY o.id
)
SELECT 'hour', strftime('%Y-%m-%dT%H', updated_at, '-5 hours'),
       COUNT(*), SUM(total), MAX(updated_at) FROM order_totals
GROUP BY strftime('%Y-%m-%dT%H', updated_at, '-5 hours')
UNION ALL
SELECT 'day', strftime('%Y-%m-%d', updated_at, '-5 hours'),
       COUNT(*), SUM(total), MAX(updated_at) FROM order_totals
GROUP BY strftime('%Y-%m-%d', updated_at, '-5 hours')
UNION ALL
SELECT 'month', strftime('%Y-%m', updated_at, '-5 hours'),
       COUNT(*), SUM(total), MAX(updated_at) FROM order_totals
GROUP BY strftime('%Y-%m', updated_at, '-5 hours')
UNION ALL
SELECT 'year', strftime('%Y', updated_at, '-5 hours'),
       COUNT(*), SUM(total), MAX(updated_at) FROM order_totals
GROUP BY strftime('%Y', updated_at, '-5 hours');

INSERT INTO daily_product_sales
  (day_key, product_id, product_name, unit_value, quantity, total_sales, updated_at)
SELECT strftime('%Y-%m-%d', o.updated_at, '-5 hours'), p.id, p.name,
       CAST(ROUND(SUM(COALESCE(oi.unit_value, p.value) * oi.quantity) * 1.0 /
                  SUM(oi.quantity)) AS INTEGER),
       SUM(oi.quantity), SUM(COALESCE(oi.unit_value, p.value) * oi.quantity),
       MAX(o.updated_at)
FROM orders o JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
WHERE o.status = 'closed'
GROUP BY strftime('%Y-%m-%d', o.updated_at, '-5 hours'), p.id;

INSERT INTO daily_category_sales
  (day_key, category_id, category_name, quantity, total_sales, updated_at)
SELECT strftime('%Y-%m-%d', o.updated_at, '-5 hours'), category.id, category.name,
       SUM(oi.quantity), SUM(COALESCE(oi.unit_value, p.value) * oi.quantity),
       MAX(o.updated_at)
FROM orders o JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
JOIN menu_categories category ON category.id = p.category_id
WHERE o.status = 'closed'
GROUP BY strftime('%Y-%m-%d', o.updated_at, '-5 hours'), category.id;
