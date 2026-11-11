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

-- Cuatro salones del restaurante.
INSERT INTO hall (id, name) VALUES
  (9001, 'Salón Principal'),
  (9002, 'Terraza'),
  (9003, 'Salón Familiar'),
  (9004, 'Barra');

-- Empleados no administradores quedan asignados a todos los salones.
INSERT INTO employee_halls (user_id, hall_id) VALUES
  (9002, 9001), (9002, 9002), (9002, 9003), (9002, 9004),
  (9011, 9001), (9011, 9002), (9011, 9003), (9011, 9004),
  (9012, 9001), (9012, 9002), (9012, 9003), (9012, 9004),
  (9013, 9001), (9013, 9002), (9013, 9003), (9013, 9004),
  (9014, 9001), (9014, 9002), (9014, 9003), (9014, 9004);

-- Mesas por salón (entre 5 y 10 cada uno).
-- Salón Principal (9001): 8 mesas.
INSERT INTO hall_tables
  (id, identifier, x, y, width, height, rotation, status, hall_id)
VALUES
  (9101, 'P-01',  40,  60, 120, 80, 0, 'available', 9001),
  (9102, 'P-02', 220,  60, 120, 80, 0, 'available', 9001),
  (9103, 'P-03', 400,  60, 120, 80, 0, 'waiting',   9001),
  (9104, 'P-04', 580,  60, 120, 80, 0, 'available', 9001),
  (9105, 'P-05',  40, 200, 120, 80, 0, 'eating',    9001),
  (9106, 'P-06', 220, 200, 120, 80, 0, 'available', 9001),
  (9107, 'P-07', 400, 200, 120, 80, 0, 'available', 9001),
  (9108, 'P-08', 580, 200, 120, 80, 0, 'available', 9001),
-- Terraza (9002): 6 mesas.
  (9201, 'T-01',  40,  60, 100, 100, 0, 'available', 9002),
  (9202, 'T-02', 200,  60, 100, 100, 0, 'available', 9002),
  (9203, 'T-03', 360,  60, 100, 100, 0, 'waiting',   9002),
  (9204, 'T-04',  40, 220, 100, 100, 0, 'available', 9002),
  (9205, 'T-05', 200, 220, 100, 100, 0, 'available', 9002),
  (9206, 'T-06', 360, 220, 100, 100, 0, 'eating',    9002),
-- Salón Familiar (9003): 10 mesas.
  (9301, 'F-01',  40,  60, 140, 100, 0, 'available', 9003),
  (9302, 'F-02', 240,  60, 140, 100, 0, 'available', 9003),
  (9303, 'F-03', 440,  60, 140, 100, 0, 'available', 9003),
  (9304, 'F-04', 640,  60, 140, 100, 0, 'waiting',   9003),
  (9305, 'F-05',  40, 220, 140, 100, 0, 'available', 9003),
  (9306, 'F-06', 240, 220, 140, 100, 0, 'available', 9003),
  (9307, 'F-07', 440, 220, 140, 100, 0, 'eating',    9003),
  (9308, 'F-08', 640, 220, 140, 100, 0, 'available', 9003),
  (9309, 'F-09',  40, 380, 140, 100, 0, 'available', 9003),
  (9310, 'F-10', 240, 380, 140, 100, 0, 'available', 9003),
-- Barra (9004): 5 mesas (asientos de barra).
  (9401, 'B-01',  40, 60, 70, 70, 0, 'available', 9004),
  (9402, 'B-02', 130, 60, 70, 70, 0, 'available', 9004),
  (9403, 'B-03', 220, 60, 70, 70, 0, 'waiting',   9004),
  (9404, 'B-04', 310, 60, 70, 70, 0, 'available', 9004),
  (9405, 'B-05', 400, 60, 70, 70, 0, 'available', 9004);

INSERT INTO hall_walls
  (id, hall_id, x, y, width, height, rotation)
VALUES
  (9001, 9001, 20, 20, 700, 8, 0),
  (9002, 9002, 20, 20, 480, 8, 0),
  (9003, 9003, 20, 20, 800, 8, 0),
  (9004, 9004, 20, 20, 460, 8, 0);

-- Un grupo de mesas de ejemplo en el Salón Principal.
INSERT INTO table_groups
  (id, hall_id, visible_identifier, status, created_at)
VALUES (9001, 9001, 'P-01, P-02', 'waiting', CURRENT_TIMESTAMP);

INSERT INTO table_group_members (group_id, table_id) VALUES
  (9001, 9101),
  (9001, 9102);

-- Ingredientes para armar y personalizar los productos.
INSERT INTO ingredient_categories (id, name) VALUES
  (9001, 'Proteínas'),
  (9002, 'Vegetales'),
  (9003, 'Salsas'),
  (9004, 'Lácteos'),
  (9005, 'Acompañamientos');

INSERT INTO ingredients (id, name, description, category_id) VALUES
  (9001, 'Carne de res',        'Carne 100% de res', 9001),
  (9002, 'Pollo apanado',       NULL,                9001),
  (9003, 'Tocineta',            NULL,                9001),
  (9004, 'Salchicha',           NULL,                9001),
  (9005, 'Lechuga',             NULL,                9002),
  (9006, 'Tomate',              NULL,                9002),
  (9007, 'Cebolla',             NULL,                9002),
  (9008, 'Queso cheddar',       NULL,                9004),
  (9009, 'Salsa de la casa',    NULL,                9003),
  (9010, 'Salsa BBQ',           NULL,                9003),
  (9011, 'Papa a la francesa',  NULL,                9005);

-- Menú principal compartido por los cuatro salones.
INSERT INTO menu (id, name) VALUES (9001, 'Menú Principal');

INSERT INTO menu_halls (menu_id, hall_id, is_primary) VALUES
  (9001, 9001, 1),
  (9001, 9002, 1),
  (9001, 9003, 1),
  (9001, 9004, 1);

-- Categorías del menú. Las especiales (is_special = 1) agrupan adiciones.
INSERT INTO menu_categories
  (id, menu_id, name, parent_category_id, is_special, position)
VALUES
  (9001, 9001, 'Hamburguesas',   NULL, 0, 0),
  (9002, 9001, 'Salchipapas',    NULL, 0, 1),
  (9003, 9001, 'Papas',          NULL, 0, 2),
  (9004, 9001, 'Bebidas',        NULL, 0, 3),
  (9005, 9001, 'Adiciones',      NULL, 1, 4);

INSERT INTO products
  (id, name, description, value, menu_id, category_id, is_active)
VALUES
  -- Hamburguesas (9001)
  (9001, 'Hamburguesa Clásica',   'Carne de res, lechuga, tomate y salsa de la casa',       18000, 9001, 9001, 1),
  (9002, 'Hamburguesa Doble',     'Doble carne, doble queso cheddar y tocineta',            26000, 9001, 9001, 1),
  (9003, 'Hamburguesa de Pollo',  'Pechuga de pollo apanada, lechuga y salsa de la casa',   19000, 9001, 9001, 1),
  (9004, 'Hamburguesa BBQ',       'Carne de res, tocineta, cebolla y salsa BBQ',            22000, 9001, 9001, 1),
  -- Salchipapas (9002)
  (9005, 'Salchipapa Sencilla',   'Papa a la francesa con salchicha y salsas',              14000, 9001, 9002, 1),
  (9006, 'Salchipapa Especial',   'Papa, salchicha, carne desmechada, queso y tocineta',    21000, 9001, 9002, 1),
  -- Papas (9003)
  (9007, 'Papas a la Francesa',   'Porción de papa a la francesa',                           8000, 9001, 9003, 1),
  (9008, 'Papas con Queso',       'Papa a la francesa con queso cheddar fundido',           11000, 9001, 9003, 1),
  -- Bebidas (9004)
  (9009, 'Gaseosa 400ml',         'Bebida gaseosa personal',                                 4000, 9001, 9004, 1),
  (9010, 'Limonada Natural',      'Limonada natural de 500ml',                               6000, 9001, 9004, 1),
  (9011, 'Jugo Natural',          'Jugo natural en agua o leche',                            6500, 9001, 9004, 1),
  (9012, 'Cerveza Nacional',      'Cerveza nacional 330ml',                                  7000, 9001, 9004, 1),
  -- Adiciones (categoría especial 9005)
  (9013, 'Adición Tocineta',      NULL,                                                      4500, 9001, 9005, 1),
  (9014, 'Adición Queso Cheddar', NULL,                                                      3500, 9001, 9005, 1),
  (9015, 'Adición Carne',         NULL,                                                      6000, 9001, 9005, 1),
  -- Producto inactivo para pruebas de visibilidad.
  (9016, 'Producto inactivo',     'No debe aparecer en el menú activo',                      1000, 9001, 9001, 0);

-- Orden de los productos dentro de cada categoría.
INSERT INTO category_product_positions (category_id, product_id, position) VALUES
  (9001, 9001, 0),
  (9001, 9002, 1),
  (9001, 9003, 2),
  (9001, 9004, 3),
  (9001, 9016, 4),
  (9002, 9005, 0),
  (9002, 9006, 1),
  (9003, 9007, 0),
  (9003, 9008, 1),
  (9004, 9009, 0),
  (9004, 9010, 1),
  (9004, 9011, 2),
  (9004, 9012, 3),
  (9005, 9013, 0),
  (9005, 9014, 1),
  (9005, 9015, 2);

-- Ingredientes que componen cada producto (permite personalizar/quitar).
INSERT INTO product_ingredients (product_id, ingredient_id) VALUES
  (9001, 9001), (9001, 9005), (9001, 9006), (9001, 9009),
  (9002, 9001), (9002, 9003), (9002, 9008),
  (9003, 9002), (9003, 9005), (9003, 9009),
  (9004, 9001), (9004, 9003), (9004, 9007), (9004, 9010),
  (9005, 9004), (9005, 9011), (9005, 9009),
  (9006, 9004), (9006, 9011), (9006, 9008), (9006, 9003),
  (9007, 9011),
  (9008, 9011), (9008, 9008),
  (9013, 9003),
  (9014, 9008),
  (9015, 9001);
