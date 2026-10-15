package com.orderflow.paymentservice.dto;

import com.orderflow.paymentservice.entity.Payment;
import com.orderflow.paymentservice.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
        UUID orderId,
        BigDecimal amount,
        PaymentStatus status,
        String declineReason
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getOrderId(), payment.getAmount(), payment.getStatus(), payment.getDeclineReason());
    }
}
