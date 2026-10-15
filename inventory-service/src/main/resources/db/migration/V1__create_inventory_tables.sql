CREATE TABLE inventory_items (
    product_id        UUID PRIMARY KEY,
    quantity_on_hand  INTEGER NOT NULL CHECK (quantity_on_hand >= 0),
    quantity_reserved INTEGER NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_reserved_within_on_hand CHECK (quantity_reserved <= quantity_on_hand)
);

CREATE TABLE stock_reservations (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL,
    product_id  UUID NOT NULL,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_stock_reservations_order ON stock_reservations(order_id);
