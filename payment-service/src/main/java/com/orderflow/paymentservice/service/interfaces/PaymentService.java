package com.orderflow.paymentservice.service.interfaces;

import com.orderflow.paymentservice.dto.ChargeRequest;
import com.orderflow.paymentservice.dto.PaymentResponse;

import java.util.UUID;

public interface PaymentService {

    PaymentResponse charge(ChargeRequest request);

    PaymentResponse refund(UUID orderId);

    PaymentResponse getByOrderId(UUID orderId);
}
