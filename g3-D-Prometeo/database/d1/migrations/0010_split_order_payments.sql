CREATE TABLE order_split_accounts (
  id INTEGER PRIMARY KEY,
  order_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'paid')),
  payment_method TEXT CHECK (payment_method IN ('cash', 'transfer', 'card')),
  tip_percent INTEGER NOT NULL DEFAULT 0 CHECK (tip_percent BETWEEN 0 AND 100),
  tip_value INTEGER NOT NULL DEFAULT 0 CHECK (tip_value >= 0),
  subtotal INTEGER NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  total_value INTEGER NOT NULL DEFAULT 0 CHECK (total_value >= 0),
  paid_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (order_id, position),
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX order_split_accounts_order_status_idx
  ON order_split_accounts(order_id, status, position);
CREATE INDEX order_split_accounts_paid_at_idx
  ON order_split_accounts(paid_at DESC);

CREATE TABLE order_split_allocations (
  account_id INTEGER NOT NULL,
  order_item_id INTEGER NOT NULL,
  half_units INTEGER NOT NULL CHECK (half_units > 0),
  PRIMARY KEY (account_id, order_item_id),
  FOREIGN KEY (account_id) REFERENCES order_split_accounts(id) ON DELETE CASCADE,
  FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE CASCADE
);

CREATE INDEX order_split_allocations_item_idx
  ON order_split_allocations(order_item_id, account_id);

-- Existing billed orders become a single paid account so old history and the
-- new split-payment history share one representation.
INSERT INTO order_split_accounts
  (order_id, name, position, status, payment_method, tip_percent, tip_value,
   subtotal, total_value, paid_at, created_at, updated_at)
SELECT id, 'Cuenta completa', 0, 'paid', payment_method, 0, 0,
       total_value, total_value, updated_at, created_at, updated_at
FROM orders WHERE status = 'closed';

INSERT INTO order_split_allocations(account_id, order_item_id, half_units)
SELECT account.id, item.id, item.quantity * 2
FROM order_split_accounts account
JOIN order_items item ON item.order_id = account.order_id
WHERE account.status = 'paid';
