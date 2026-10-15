package com.orderflow.paymentservice.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.paymentservice.dto.ChargeRequest;
import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.entity.Payment;
import com.orderflow.paymentservice.entity.PaymentStatus;
import com.orderflow.paymentservice.repository.PaymentRepository;
import com.orderflow.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository);
    }

    @Test
    void chargeWithinLimitIsCaptured() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.charge(new ChargeRequest(orderId, new BigDecimal("250.00")));

        assertThat(response.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.declineReason()).isNull();
    }

    @Test
    void chargeAboveLimitIsDeclined() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.charge(
                new ChargeRequest(orderId, PaymentServiceImpl.PER_TRANSACTION_LIMIT.add(BigDecimal.ONE)));

        assertThat(response.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(response.declineReason()).contains("per-transaction limit");
    }

    @Test
    void repeatedChargeReturnsOriginalOutcomeWithoutChargingAgain() {
        UUID orderId = UUID.randomUUID();
        Payment existing = new Payment(orderId, new BigDecimal("40.00"));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.charge(new ChargeRequest(orderId, new BigDecimal("40.00")));

        assertThat(response.status()).isEqualTo(PaymentStatus.CAPTURED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundOfCapturedPaymentMarksRefunded() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(new Payment(orderId, new BigDecimal("40.00"))));

        assertThat(paymentService.refund(orderId).status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundOfDeclinedPaymentIsRejected() {
        UUID orderId = UUID.randomUUID();
        Payment declined = new Payment(orderId, new BigDecimal("99999.00"));
        declined.decline("over limit");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> paymentService.refund(orderId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void refundOfUnknownOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refund(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
