CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    event_id    UUID NOT NULL UNIQUE,
    order_id    UUID NOT NULL,
    recipient   VARCHAR(255) NOT NULL,
    channel     VARCHAR(16) NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient, created_at);
