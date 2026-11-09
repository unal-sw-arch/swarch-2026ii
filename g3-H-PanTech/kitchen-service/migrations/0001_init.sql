CREATE SCHEMA IF NOT EXISTS kitchen;

CREATE TABLE IF NOT EXISTS kitchen.prep_times (
    product_id   uuid PRIMARY KEY,
    prep_minutes integer NOT NULL CHECK (prep_minutes > 0),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS kitchen.order_status_events (
    id         bigserial PRIMARY KEY,
    order_id   uuid NOT NULL,
    status     varchar(20) NOT NULL CHECK (status IN ('pendiente', 'en_preparacion', 'listo', 'entregado')),
    changed_at timestamptz NOT NULL DEFAULT now(),
    changed_by uuid
);

CREATE INDEX IF NOT EXISTS idx_order_status_events_order
    ON kitchen.order_status_events (order_id, changed_at);

CREATE TABLE IF NOT EXISTS kitchen.kitchen_capacity (
    id         smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    max_orders integer NOT NULL CHECK (max_orders > 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);
