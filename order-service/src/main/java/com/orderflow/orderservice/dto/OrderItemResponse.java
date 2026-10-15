package com.orderflow.orderservice.dto;

import com.orderflow.orderservice.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID productId, BigDecimal unitPrice, int quantity) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getUnitPrice(), item.getQuantity());
    }
}
