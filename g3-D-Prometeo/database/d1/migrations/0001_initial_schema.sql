-- Canonical cloud schema. This represents the current end state directly;
-- legacy Node/SQLite repair migrations intentionally do not run in D1.

CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  role TEXT NOT NULL,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL
);

CREATE TABLE admin_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX admin_sessions_expires_at_idx
  ON admin_sessions(expires_at);

CREATE TABLE device_pairing_requests (
  id TEXT PRIMARY KEY,
  secret_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  used_at TEXT
);

CREATE INDEX device_pairing_requests_expires_at_idx
  ON device_pairing_requests(expires_at);

CREATE TABLE paired_devices (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  certificate_fingerprint TEXT NOT NULL UNIQUE,
  certificate_serial TEXT NOT NULL,
  certificate_pem TEXT NOT NULL,
  paired_at TEXT NOT NULL,
  revoked_at TEXT
);

CREATE TABLE employee_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  device_id INTEGER NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (device_id) REFERENCES paired_devices(id) ON DELETE CASCADE
);

CREATE INDEX employee_sessions_expires_at_idx
  ON employee_sessions(expires_at);

CREATE TABLE hall (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE hall_tables (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  identifier TEXT NOT NULL,
  x REAL NOT NULL,
  y REAL NOT NULL,
  width REAL NOT NULL,
  height REAL NOT NULL,
  rotation REAL NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'available',
  hall_id INTEGER NOT NULL,
  UNIQUE (hall_id, identifier),
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE TABLE hall_walls (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  hall_id INTEGER NOT NULL,
  x REAL NOT NULL,
  y REAL NOT NULL,
  width REAL NOT NULL,
  height REAL NOT NULL,
  rotation REAL NOT NULL DEFAULT 0,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE TABLE table_groups (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  hall_id INTEGER NOT NULL,
  visible_identifier TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'available'
    CHECK (status IN ('available', 'waiting', 'eating')),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE TABLE table_group_members (
  group_id INTEGER NOT NULL,
  table_id INTEGER NOT NULL UNIQUE,
  PRIMARY KEY (group_id, table_id),
  FOREIGN KEY (group_id) REFERENCES table_groups(id) ON DELETE CASCADE,
  FOREIGN KEY (table_id) REFERENCES hall_tables(id) ON DELETE CASCADE
);

CREATE TABLE employee_halls (
  user_id INTEGER NOT NULL,
  hall_id INTEGER NOT NULL,
  PRIMARY KEY (user_id, hall_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE INDEX employee_halls_hall_id_idx ON employee_halls(hall_id);

CREATE TABLE ingredient_categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE ingredients (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE,
  description TEXT,
  category_id INTEGER NOT NULL,
  FOREIGN KEY (category_id)
    REFERENCES ingredient_categories(id) ON DELETE RESTRICT
);

CREATE INDEX ingredients_category_id_idx ON ingredients(category_id);

CREATE TABLE menu (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE menu_halls (
  menu_id INTEGER NOT NULL,
  hall_id INTEGER NOT NULL,
  is_primary INTEGER NOT NULL DEFAULT 0 CHECK (is_primary IN (0, 1)),
  PRIMARY KEY (menu_id, hall_id),
  FOREIGN KEY (menu_id) REFERENCES menu(id) ON DELETE CASCADE,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE INDEX menu_halls_hall_id_idx ON menu_halls(hall_id);

CREATE UNIQUE INDEX menu_halls_one_primary_per_hall_idx
  ON menu_halls(hall_id) WHERE is_primary = 1;

CREATE TABLE menu_categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  menu_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  parent_category_id INTEGER,
  is_special INTEGER NOT NULL DEFAULT 0 CHECK (is_special IN (0, 1)),
  position INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0),
  FOREIGN KEY (menu_id) REFERENCES menu(id) ON DELETE CASCADE,
  FOREIGN KEY (parent_category_id)
    REFERENCES menu_categories(id) ON DELETE CASCADE
);

CREATE INDEX menu_categories_menu_id_idx ON menu_categories(menu_id);

CREATE TABLE products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  description TEXT,
  value INTEGER NOT NULL CHECK (value >= 0),
  menu_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
  FOREIGN KEY (menu_id) REFERENCES menu(id) ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES menu_categories(id) ON DELETE RESTRICT
);

CREATE INDEX products_category_id_idx ON products(category_id);

CREATE TABLE category_product_positions (
  category_id INTEGER NOT NULL,
  product_id INTEGER NOT NULL,
  position INTEGER NOT NULL CHECK (position >= 0),
  PRIMARY KEY (category_id, product_id),
  UNIQUE (category_id, position),
  FOREIGN KEY (category_id) REFERENCES menu_categories(id) ON DELETE CASCADE,
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX category_product_positions_product_id_idx
  ON category_product_positions(product_id);

CREATE TABLE product_halls (
  product_id INTEGER NOT NULL,
  hall_id INTEGER NOT NULL,
  PRIMARY KEY (product_id, hall_id),
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE INDEX product_halls_hall_id_idx ON product_halls(hall_id);

CREATE TABLE product_ingredients (
  product_id INTEGER NOT NULL,
  ingredient_id INTEGER NOT NULL,
  PRIMARY KEY (product_id, ingredient_id),
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT
);

CREATE TABLE orders (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  author_id INTEGER NOT NULL,
  table_id INTEGER,
  table_group_id INTEGER,
  hall_id INTEGER,
  external_name TEXT,
  description TEXT,
  receiver TEXT,
  status TEXT NOT NULL DEFAULT 'waiting'
    CHECK (status IN ('waiting', 'eating', 'closed')),
  payment_method TEXT CHECK (payment_method IN ('cash', 'transfer', 'card')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  CHECK ((external_name IS NULL AND table_id IS NOT NULL)
    OR (external_name IS NOT NULL AND table_id IS NULL
      AND table_group_id IS NULL AND hall_id IS NOT NULL)),
  FOREIGN KEY (author_id) REFERENCES users(id),
  FOREIGN KEY (table_id) REFERENCES hall_tables(id),
  FOREIGN KEY (table_group_id) REFERENCES table_groups(id) ON DELETE SET NULL,
  FOREIGN KEY (hall_id) REFERENCES hall(id)
);

CREATE INDEX orders_table_status_idx ON orders(table_id, status);
CREATE INDEX orders_table_group_status_idx ON orders(table_group_id, status);

CREATE TABLE order_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id INTEGER NOT NULL,
  product_id INTEGER NOT NULL,
  quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
  delivered_quantity INTEGER NOT NULL DEFAULT 0
    CHECK (delivered_quantity >= 0 AND delivered_quantity <= quantity),
  specifications TEXT,
  parent_order_item_id INTEGER,
  status TEXT NOT NULL DEFAULT 'ordered'
    CHECK (status IN ('ordered', 'delivered')),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (parent_order_item_id) REFERENCES order_items(id) ON DELETE CASCADE
);

CREATE INDEX order_items_order_id_idx ON order_items(order_id);
CREATE INDEX order_items_parent_order_item_id_idx
  ON order_items(parent_order_item_id);

CREATE TABLE order_item_deliveries (
  order_item_id INTEGER NOT NULL,
  unit_index INTEGER NOT NULL CHECK (unit_index >= 0),
  delivered_at TEXT NOT NULL,
  delivered_by INTEGER,
  PRIMARY KEY (order_item_id, unit_index),
  FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
  FOREIGN KEY (delivered_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE order_item_removed_ingredients (
  order_item_id INTEGER NOT NULL,
  ingredient_id INTEGER NOT NULL,
  PRIMARY KEY (order_item_id, ingredient_id),
  FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE,
  FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT
);

CREATE TABLE removed_order_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id INTEGER NOT NULL,
  product_name TEXT NOT NULL,
  category_name TEXT,
  product_description TEXT,
  unit_value INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  specifications TEXT,
  parent_product_name TEXT,
  removed_at TEXT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE order_modifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id INTEGER NOT NULL,
  order_item_id INTEGER,
  modifier_id INTEGER NOT NULL,
  modification_type TEXT NOT NULL,
  old_value TEXT,
  new_value TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  FOREIGN KEY (modifier_id) REFERENCES users(id)
);

CREATE TABLE activity_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  author_id INTEGER,
  hall_id INTEGER,
  type TEXT NOT NULL,
  modification TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY (hall_id) REFERENCES hall(id) ON DELETE CASCADE
);

CREATE INDEX activity_log_created_at_idx ON activity_log(created_at DESC);

CREATE TRIGGER menu_categories_special_root_insert
BEFORE INSERT ON menu_categories
WHEN NEW.parent_category_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM menu_categories AS parent
  WHERE parent.id = NEW.parent_category_id
    AND parent.menu_id = NEW.menu_id
    AND parent.is_special = NEW.is_special
)
BEGIN
  SELECT RAISE(ABORT, 'INVALID_PARENT_CATEGORY');
END;

CREATE TRIGGER menu_categories_special_root_update
BEFORE UPDATE OF parent_category_id, is_special ON menu_categories
WHEN (NEW.parent_category_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM menu_categories AS parent
  WHERE parent.id = NEW.parent_category_id
    AND parent.menu_id = NEW.menu_id
    AND parent.is_special = NEW.is_special
)) OR NEW.parent_category_id = NEW.id OR NEW.parent_category_id IN (
  WITH RECURSIVE descendants(id) AS (
    SELECT id FROM menu_categories WHERE parent_category_id = OLD.id
    UNION ALL
    SELECT child.id FROM menu_categories AS child
    JOIN descendants ON child.parent_category_id = descendants.id
  )
  SELECT id FROM descendants
) OR EXISTS (
  SELECT 1 FROM menu_categories AS child
  WHERE child.parent_category_id = OLD.id
    AND (child.menu_id != NEW.menu_id OR child.is_special != NEW.is_special)
)
BEGIN
  SELECT RAISE(ABORT, 'INVALID_PARENT_CATEGORY');
END;

CREATE TRIGGER order_items_special_parent_insert
BEFORE INSERT ON order_items
WHEN NEW.parent_order_item_id IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'INVALID_SPECIAL_PRODUCT_PARENT')
  WHERE NOT EXISTS (
    SELECT 1 FROM order_items AS parent
    WHERE parent.id = NEW.parent_order_item_id
      AND parent.order_id = NEW.order_id
      AND EXISTS (
        SELECT 1 FROM products AS parent_product
        JOIN menu_categories AS parent_category
          ON parent_category.id = parent_product.category_id
        WHERE parent_product.id = parent.product_id
          AND parent_category.is_special = 0
      )
  ) OR NOT EXISTS (
    SELECT 1 FROM products AS child_product
    JOIN menu_categories AS child_category
      ON child_category.id = child_product.category_id
    WHERE child_product.id = NEW.product_id
      AND child_category.is_special = 1
  );
END;

CREATE TRIGGER order_items_special_parent_update
BEFORE UPDATE OF order_id, product_id, parent_order_item_id ON order_items
WHEN NEW.parent_order_item_id IS NOT NULL
BEGIN
  SELECT RAISE(ABORT, 'INVALID_SPECIAL_PRODUCT_PARENT')
  WHERE NOT EXISTS (
    SELECT 1 FROM order_items AS parent
    WHERE parent.id = NEW.parent_order_item_id
      AND parent.order_id = NEW.order_id
      AND EXISTS (
        SELECT 1 FROM products AS parent_product
        JOIN menu_categories AS parent_category
          ON parent_category.id = parent_product.category_id
        WHERE parent_product.id = parent.product_id
          AND parent_category.is_special = 0
      )
  ) OR NOT EXISTS (
    SELECT 1 FROM products AS child_product
    JOIN menu_categories AS child_category
      ON child_category.id = child_product.category_id
    WHERE child_product.id = NEW.product_id
      AND child_category.is_special = 1
  );
END;
