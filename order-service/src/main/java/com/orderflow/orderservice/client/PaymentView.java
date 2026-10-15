package com.orderflow.orderservice.client;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentView(UUID orderId, BigDecimal amount, String status, String declineReason) {
}
