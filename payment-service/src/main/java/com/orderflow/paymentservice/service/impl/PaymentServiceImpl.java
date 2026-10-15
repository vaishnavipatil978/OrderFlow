package com.orderflow.paymentservice.service.impl;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.paymentservice.dto.ChargeRequest;
import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.entity.Payment;
import com.orderflow.paymentservice.entity.PaymentStatus;
import com.orderflow.paymentservice.repository.PaymentRepository;
import com.orderflow.paymentservice.service.interfaces.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulated gateway, deterministic on purpose: a charge is declined only when it breaches the
 * per-transaction limit, so tests can exercise both the success and compensation paths without
 * flakiness. A real gateway adapter would replace the decision in {@link #charge}.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    public static final BigDecimal PER_TRANSACTION_LIMIT = new BigDecimal("10000.00");

    private final PaymentRepository paymentRepository;

    /** Idempotent per order: a repeated charge returns the original outcome rather than charging twice. */
    @Override
    @Transactional
    public PaymentResponse charge(ChargeRequest request) {
        Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }
        Payment payment = new Payment(request.orderId(), request.amount());
        if (request.amount().compareTo(PER_TRANSACTION_LIMIT) > 0) {
            payment.decline("Amount exceeds the per-transaction limit of " + PER_TRANSACTION_LIMIT.toPlainString());
        }
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse refund(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new ConflictException("Only captured payments can be refunded");
        }
        payment.refund();
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
    }
}
