CREATE TABLE sales_metric_buckets_with_minutes (
  period TEXT NOT NULL CHECK (period IN ('minute', 'hour', 'day', 'month', 'year')),
  bucket_key TEXT NOT NULL,
  order_count INTEGER NOT NULL DEFAULT 0 CHECK (order_count >= 0),
  total_sales INTEGER NOT NULL DEFAULT 0 CHECK (total_sales >= 0),
  updated_at TEXT NOT NULL,
  PRIMARY KEY (period, bucket_key)
);

INSERT INTO sales_metric_buckets_with_minutes
  (period, bucket_key, order_count, total_sales, updated_at)
SELECT period, bucket_key, order_count, total_sales, updated_at
FROM sales_metric_buckets;

INSERT INTO sales_metric_buckets_with_minutes
  (period, bucket_key, order_count, total_sales, updated_at)
SELECT 'minute', strftime('%Y-%m-%dT%H:%M', updated_at, '-5 hours'),
       COUNT(*), SUM(total_value), MAX(updated_at)
FROM orders
WHERE status = 'closed'
GROUP BY strftime('%Y-%m-%dT%H:%M', updated_at, '-5 hours');

DROP TABLE sales_metric_buckets;
ALTER TABLE sales_metric_buckets_with_minutes RENAME TO sales_metric_buckets;
