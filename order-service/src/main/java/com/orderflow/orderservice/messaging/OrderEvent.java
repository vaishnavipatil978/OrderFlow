package com.orderflow.orderservice.messaging;

import com.orderflow.orderservice.entity.CustomerOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published to {@link #TOPIC}. Notification-service keeps its own copy of this shape rather
 * than importing this class - services share the wire format, not a compile-time type.
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

    public static final String TOPIC = "order.events";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    public static OrderEvent of(String eventType, CustomerOrder order) {
        return new OrderEvent(
                UUID.randomUUID(),
                eventType,
                order.getId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getFailureReason(),
                Instant.now());
    }
}
