package com.orderflow.orderservice.dto;

import com.orderflow.orderservice.entity.CustomerOrder;
import com.orderflow.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        String failureReason,
        List<OrderItemResponse> items,
        Instant createdAt
) {

    /** Must be called inside a transaction: maps the lazily loaded items. */
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getFailureReason(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt());
    }
}
