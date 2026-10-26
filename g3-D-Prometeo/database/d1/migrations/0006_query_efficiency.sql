CREATE INDEX IF NOT EXISTS menu_name_idx
  ON menu(name);

CREATE INDEX IF NOT EXISTS menu_categories_parent_id_idx
  ON menu_categories(parent_category_id);

CREATE INDEX IF NOT EXISTS menu_categories_lookup_idx
  ON menu_categories(menu_id, parent_category_id, name, is_special);

CREATE INDEX IF NOT EXISTS products_menu_active_category_idx
  ON products(menu_id, is_active, category_id);

CREATE INDEX IF NOT EXISTS products_catalog_lookup_idx
  ON products(menu_id, category_id, name);

CREATE INDEX IF NOT EXISTS product_halls_hall_product_idx
  ON product_halls(hall_id, product_id);
