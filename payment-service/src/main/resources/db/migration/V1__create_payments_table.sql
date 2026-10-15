CREATE TABLE payments (
    id             UUID PRIMARY KEY,
    order_id       UUID NOT NULL UNIQUE,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status         VARCHAR(16) NOT NULL,
    decline_reason VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0
);
