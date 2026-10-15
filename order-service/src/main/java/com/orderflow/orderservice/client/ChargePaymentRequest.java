package com.orderflow.orderservice.client;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargePaymentRequest(UUID orderId, BigDecimal amount) {
}
