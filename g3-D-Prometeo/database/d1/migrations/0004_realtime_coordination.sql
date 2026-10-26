CREATE TABLE resource_versions (
  resource TEXT PRIMARY KEY,
  version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
  updated_at TEXT NOT NULL
);

CREATE TABLE mutation_receipts (
  actor_id INTEGER NOT NULL,
  idempotency_key TEXT NOT NULL,
  operation TEXT NOT NULL,
  status INTEGER NOT NULL,
  response_body TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY (actor_id, idempotency_key),
  FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX mutation_receipts_created_at_idx
  ON mutation_receipts(created_at);

CREATE TRIGGER realtime_orders_insert AFTER INSERT ON orders BEGIN
  INSERT INTO resource_versions(resource, version, updated_at)
  VALUES ('hall:' || COALESCE(NEW.hall_id,
    (SELECT hall_id FROM hall_tables WHERE id = NEW.table_id)) || ':orders',
    1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET
    version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_orders_update AFTER UPDATE ON orders BEGIN
  INSERT INTO resource_versions(resource, version, updated_at)
  VALUES ('hall:' || COALESCE(NEW.hall_id,
    (SELECT hall_id FROM hall_tables WHERE id = NEW.table_id)) || ':orders',
    1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET
    version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_orders_delete AFTER DELETE ON orders BEGIN
  INSERT INTO resource_versions(resource, version, updated_at)
  VALUES ('hall:' || COALESCE(OLD.hall_id,
    (SELECT hall_id FROM hall_tables WHERE id = OLD.table_id)) || ':orders',
    1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET
    version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_dashboard_billing AFTER UPDATE OF status ON orders
WHEN OLD.status != 'closed' AND NEW.status = 'closed' BEGIN
  INSERT INTO resource_versions(resource, version, updated_at)
  VALUES ('dashboard', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET
    version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_activity_insert AFTER INSERT ON activity_log BEGIN
  INSERT INTO resource_versions(resource, version, updated_at)
  VALUES ('activity', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET
    version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_catalog_menu_insert AFTER INSERT ON menu BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_menu_update AFTER UPDATE ON menu BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_menu_delete AFTER DELETE ON menu BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_catalog_category_insert AFTER INSERT ON menu_categories BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_category_update AFTER UPDATE ON menu_categories BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_category_delete AFTER DELETE ON menu_categories BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_catalog_product_insert AFTER INSERT ON products BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_update AFTER UPDATE ON products BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_delete AFTER DELETE ON products BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_catalog_ingredient_category_insert
AFTER INSERT ON ingredient_categories BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_ingredient_insert AFTER INSERT ON ingredients BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_catalog_menu_halls_insert AFTER INSERT ON menu_halls BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_menu_halls_delete AFTER DELETE ON menu_halls BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_halls_insert AFTER INSERT ON product_halls BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_halls_delete AFTER DELETE ON product_halls BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_ingredients_insert
AFTER INSERT ON product_ingredients BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_ingredients_delete
AFTER DELETE ON product_ingredients BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_order_insert
AFTER INSERT ON category_product_positions BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_catalog_product_order_delete
AFTER DELETE ON category_product_positions BEGIN
  INSERT INTO resource_versions VALUES ('catalog', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_layout_table_insert AFTER INSERT ON hall_tables BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || NEW.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_layout_table_geometry
AFTER UPDATE OF identifier, x, y, width, height, rotation ON hall_tables BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || NEW.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_layout_table_delete AFTER DELETE ON hall_tables BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || OLD.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_layout_wall_insert AFTER INSERT ON hall_walls BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || NEW.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_layout_wall_update AFTER UPDATE ON hall_walls BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || NEW.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_layout_wall_delete AFTER DELETE ON hall_walls BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || OLD.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_layout_group_insert AFTER INSERT ON table_groups BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || NEW.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_layout_group_update AFTER UPDATE ON table_groups BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || NEW.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_layout_group_delete AFTER DELETE ON table_groups BEGIN
  INSERT INTO resource_versions VALUES ('hall:' || OLD.hall_id || ':layout', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;

CREATE TRIGGER realtime_assignments_insert AFTER INSERT ON employee_halls BEGIN
  INSERT INTO resource_versions VALUES ('user:' || NEW.user_id || ':assignments', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
CREATE TRIGGER realtime_assignments_delete AFTER DELETE ON employee_halls BEGIN
  INSERT INTO resource_versions VALUES ('user:' || OLD.user_id || ':assignments', 1, CURRENT_TIMESTAMP)
  ON CONFLICT(resource) DO UPDATE SET version = version + 1, updated_at = CURRENT_TIMESTAMP;
END;
