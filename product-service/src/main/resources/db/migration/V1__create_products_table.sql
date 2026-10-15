CREATE TABLE products (
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(2000),
    sku          VARCHAR(64) NOT NULL UNIQUE,
    price        NUMERIC(12, 2) NOT NULL,
    category     VARCHAR(100) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_name_lower ON products(lower(name));
