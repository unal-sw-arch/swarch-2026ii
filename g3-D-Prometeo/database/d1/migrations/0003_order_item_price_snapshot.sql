-- A billed order must retain the price used when it was created even when the
-- catalog price changes later.
ALTER TABLE order_items ADD COLUMN unit_value INTEGER CHECK (unit_value >= 0);

UPDATE order_items
SET unit_value = (SELECT value FROM products WHERE products.id = order_items.product_id)
WHERE unit_value IS NULL;
