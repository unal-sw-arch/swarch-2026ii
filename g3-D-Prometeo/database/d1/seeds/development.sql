-- Development-only fixture for exercising the Stage 3 read API locally.
-- IDs in the 9000 range keep these rows recognizable and isolated.

DELETE FROM order_item_deliveries WHERE order_item_id IN (
  SELECT oi.id FROM order_items oi JOIN orders o ON o.id = oi.order_id
  LEFT JOIN hall_tables t ON t.id = o.table_id
  WHERE COALESCE(o.hall_id, t.hall_id) >= 9000
);
DELETE FROM order_item_removed_ingredients WHERE order_item_id IN (
  SELECT oi.id FROM order_items oi JOIN orders o ON o.id = oi.order_id
  LEFT JOIN hall_tables t ON t.id = o.table_id
  WHERE COALESCE(o.hall_id, t.hall_id) >= 9000
);
DELETE FROM order_modifications WHERE order_id IN (
  SELECT o.id FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
  WHERE COALESCE(o.hall_id, t.hall_id) >= 9000
);
DELETE FROM order_items WHERE order_id IN (
  SELECT o.id FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
  WHERE COALESCE(o.hall_id, t.hall_id) >= 9000
);
DELETE FROM removed_order_items WHERE order_id IN (
  SELECT o.id FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
  WHERE COALESCE(o.hall_id, t.hall_id) >= 9000
);
DELETE FROM orders WHERE id IN (
  SELECT o.id FROM orders o LEFT JOIN hall_tables t ON t.id = o.table_id
  WHERE COALESCE(o.hall_id, t.hall_id) >= 9000
);
DELETE FROM activity_log WHERE hall_id >= 9000;
DELETE FROM table_group_members WHERE group_id >= 9000;
DELETE FROM cloud_sessions WHERE user_id >= 9000;
DELETE FROM employee_halls WHERE user_id >= 9000 OR hall_id >= 9000;
DELETE FROM table_groups WHERE id >= 9000;
DELETE FROM hall_walls WHERE id >= 9000;
DELETE FROM hall_tables WHERE id >= 9000;
DELETE FROM category_product_positions WHERE product_id >= 9000;
DELETE FROM product_halls WHERE product_id >= 9000;
DELETE FROM product_ingredients WHERE product_id >= 9000;
DELETE FROM products WHERE id >= 9000;
DELETE FROM menu_categories WHERE id >= 9000;
DELETE FROM menu_halls WHERE menu_id >= 9000;
DELETE FROM menu WHERE id >= 9000;
DELETE FROM ingredients WHERE id >= 9000;
DELETE FROM ingredient_categories WHERE id >= 9000;
DELETE FROM hall WHERE id >= 9000;
DELETE FROM users WHERE id >= 9000;

-- Usuarios de prueba con contraseñas conocidas (ver seeds/README.md).
-- Hashes generados con el mismo scrypt del backend (domain/security/password.ts).
INSERT INTO users (id, name, role, username, password_hash) VALUES
  (9001, 'Administrador Cloud', 'admin', 'cloud.admin',
   'scrypt$131072$8$1$1aO/RzsztFURBEb/FU/JXQ==$PxRV9RhMPux34ikb9SuJ6JIkVrlYqBaVQ5JMaBxMUxO9aN/ckAbqq/16n1zvrIt75ew+4nnTLobp4TSRJ/9BHQ=='),
  (9002, 'Mesero Cloud', 'waiter', 'cloud.waiter',
   'scrypt$131072$8$1$1aO/RzsztFURBEb/FU/JXQ==$PxRV9RhMPux34ikb9SuJ6JIkVrlYqBaVQ5JMaBxMUxO9aN/ckAbqq/16n1zvrIt75ew+4nnTLobp4TSRJ/9BHQ=='),
  (9010, 'Admin de prueba', 'admin', 'admin.test',
   'scrypt$131072$8$1$UsYfjZS1tD0ICP9bavjiBQ==$pbYlZ9s3hmbQpnn6Js78p19YT/Wbde27N4NxdrfYcIht1DnOp5l/t5ubWVIi4i2RLGkS03SVqgc2JUkY5nmm+Q=='),
  (9011, 'Mesero de prueba', 'waiter', 'mesero.test',
   'scrypt$131072$8$1$EbTJPI0wY4QVEq1d6ReNMQ==$q6XzBT6/OZufOH4t882jjgTgFSLqQKr8pZ/C6nWVw70mjbJ0VpFwdWvk/Zjm4pWt1rE1ntQOuxJawF1U7o9r/A=='),
  (9012, 'Cocina de prueba', 'kitchen', 'cocina.test',
   'scrypt$131072$8$1$VbiiaSHlFL/uF6QjBXER9g==$kCy1VC9sWH9hC6i6BlDr1tHuuQMqys8gzF2YI3K2hYQ/l/w/VHqZ22UrE4DSTP3b88+D+maa5N+119KJPSS0Kg=='),
  (9013, 'Caja de prueba', 'cashier', 'caja.test',
   'scrypt$131072$8$1$+VK9Us4UuJoA8klkNFyaGQ==$KUgTYcg/WubDQDAUUMdgBtnqrKEZx5GTIFt8lwXFDFhmnlCyI2O6MUzGri2q65qMmajmohqx6YS9xp8ZZ9kKfg=='),
  (9014, 'Gerente de prueba', 'manager', 'gerente.test',
   'scrypt$131072$8$1$DcB39MMSivrZFAnomr8CkQ==$qdb/SC2E6HAGdf/Ith1HarGBMLfGgWk7s/dm0FtkMTvB8Eco8R2zlr09SdsfiJpQUJ16TxmDrHs8Z5e6k8aQVg==');

INSERT INTO hall (id, name) VALUES
  (9001, 'Salón de prueba'),
  (9002, 'Terraza de prueba');

INSERT INTO employee_halls (user_id, hall_id) VALUES
  (9002, 9001),
  (9011, 9001),
  (9012, 9001),
  (9013, 9001),
  (9014, 9001);

INSERT INTO hall_tables
  (id, identifier, x, y, width, height, rotation, status, hall_id)
VALUES
  (9001, 'T-01', 40, 60, 120, 80, 0, 'available', 9001),
  (9002, 'T-02', 220, 60, 120, 80, 0, 'waiting', 9001);

INSERT INTO hall_walls
  (id, hall_id, x, y, width, height, rotation)
VALUES (9001, 9001, 20, 20, 420, 8, 0);

INSERT INTO table_groups
  (id, hall_id, visible_identifier, status, created_at)
VALUES (9001, 9001, 'T-01, T-02', 'waiting', CURRENT_TIMESTAMP);

INSERT INTO table_group_members (group_id, table_id) VALUES
  (9001, 9001),
  (9001, 9002);

INSERT INTO ingredient_categories (id, name)
VALUES (9001, 'Proteínas de prueba');

INSERT INTO ingredients (id, name, description, category_id)
VALUES (9001, 'Carne de prueba', NULL, 9001);

INSERT INTO menu (id, name) VALUES (9001, 'Menú de prueba');

INSERT INTO menu_halls (menu_id, hall_id, is_primary) VALUES
  (9001, 9001, 1);

INSERT INTO menu_categories
  (id, menu_id, name, parent_category_id, is_special, position)
VALUES
  (9001, 9001, 'Hamburguesas', NULL, 0, 0),
  (9002, 9001, 'Grandes', 9001, 0, 0),
  (9003, 9001, 'Adiciones', NULL, 1, 1);

INSERT INTO products
  (id, name, description, value, menu_id, category_id, is_active)
VALUES
  (9001, 'Hamburguesa prueba', 'Producto activo', 20500, 9001, 9002, 1),
  (9002, 'Tocineta prueba', NULL, 4500, 9001, 9003, 1),
  (9003, 'Producto inactivo', NULL, 1000, 9001, 9001, 0);

INSERT INTO category_product_positions (category_id, product_id, position) VALUES
  (9002, 9001, 0),
  (9003, 9002, 0),
  (9001, 9003, 0);

INSERT INTO product_ingredients (product_id, ingredient_id)
VALUES (9001, 9001);
