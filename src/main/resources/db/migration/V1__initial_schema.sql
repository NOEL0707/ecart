PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS carts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CHECKED_OUT')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_carts_active_user
  ON carts(user_id)
  WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS cart_items (
  id TEXT PRIMARY KEY,
  cart_id TEXT NOT NULL,
  sku TEXT NOT NULL,
  name TEXT NOT NULL,
  unit_price INTEGER NOT NULL CHECK (unit_price >= 0),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (cart_id) REFERENCES carts(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_items_cart_sku
  ON cart_items(cart_id, sku);

CREATE TABLE IF NOT EXISTS orders (
  id TEXT PRIMARY KEY,
  order_number INTEGER NOT NULL UNIQUE,
  user_id TEXT NOT NULL,
  cart_id TEXT NOT NULL,
  subtotal INTEGER NOT NULL CHECK (subtotal >= 0),
  discount_code TEXT,
  discount_percent INTEGER,
  discount_amount INTEGER NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
  total INTEGER NOT NULL CHECK (total >= 0),
  status TEXT NOT NULL CHECK (status IN ('PLACED')),
  idempotency_key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (cart_id) REFERENCES carts(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_user_idempotency
  ON orders(user_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_order_number
  ON orders(order_number);

CREATE TABLE IF NOT EXISTS order_items (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL,
  sku TEXT NOT NULL,
  name TEXT NOT NULL,
  unit_price INTEGER NOT NULL CHECK (unit_price >= 0),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  line_total INTEGER NOT NULL CHECK (line_total >= 0),
  FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX IF NOT EXISTS ix_order_items_order_id
  ON order_items(order_id);

CREATE TABLE IF NOT EXISTS discount_codes (
  code TEXT PRIMARY KEY,
  discount_percent INTEGER NOT NULL CHECK (discount_percent BETWEEN 1 AND 100),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED')),
  triggered_by_order_number INTEGER NOT NULL UNIQUE,
  used_by_order_id TEXT,
  created_at TEXT NOT NULL,
  expires_at TEXT,
  used_at TEXT,
  FOREIGN KEY (used_by_order_id) REFERENCES orders(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_discount_codes_trigger
  ON discount_codes(triggered_by_order_number);

CREATE INDEX IF NOT EXISTS ix_discount_codes_status_expiry
  ON discount_codes(status, expires_at);
