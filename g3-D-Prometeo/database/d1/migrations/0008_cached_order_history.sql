ALTER TABLE orders ADD COLUMN total_value INTEGER NOT NULL DEFAULT 0
  CHECK (total_value >= 0);

UPDATE orders
SET hall_id = (SELECT hall_id FROM hall_tables WHERE id = orders.table_id)
WHERE hall_id IS NULL;

UPDATE orders
SET total_value = COALESCE((
  SELECT SUM(COALESCE(oi.unit_value, p.value) * oi.quantity)
  FROM order_items oi JOIN products p ON p.id = oi.product_id
  WHERE oi.order_id = orders.id
), 0);

CREATE INDEX orders_hall_status_updated_idx
  ON orders(hall_id, status, updated_at DESC, id DESC);
