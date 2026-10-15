CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_email  VARCHAR(255) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    total_amount    NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_email ON orders(customer_email);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    topic         VARCHAR(128) NOT NULL,
    payload       VARCHAR(4000) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published     BOOLEAN NOT NULL DEFAULT FALSE,
    published_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published = FALSE;
