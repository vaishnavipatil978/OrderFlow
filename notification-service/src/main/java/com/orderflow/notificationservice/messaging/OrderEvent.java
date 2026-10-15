package com.orderflow.notificationservice.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Local mirror of order-service's published event. The wire format is the contract; this service
 * never imports order-service's classes.
 */
public record OrderEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String customerEmail,
        BigDecimal totalAmount,
        String status,
        String reason,
        Instant occurredAt
) {

    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
}
