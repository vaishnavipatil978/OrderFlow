package com.orderflow.orderservice.saga;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.common.exception.ServiceUnavailableException;
import com.orderflow.orderservice.client.ChargePaymentRequest;
import com.orderflow.orderservice.client.InventoryClient;
import com.orderflow.orderservice.client.PaymentClient;
import com.orderflow.orderservice.client.PaymentView;
import com.orderflow.orderservice.client.ReserveStockRequest;
import com.orderflow.orderservice.client.StockLine;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.entity.CustomerOrder;
import com.orderflow.orderservice.messaging.OrderEvent;
import com.orderflow.orderservice.outbox.OutboxService;
import com.orderflow.orderservice.repository.CustomerOrderRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates placing an order across inventory and payment:
 * <pre>
 *   PENDING -> reserve stock -> charge payment -> commit stock -> CONFIRMED
 * </pre>
 * Each failure runs the compensations for the steps that already succeeded, then cancels the order
 * and records an outbox event. Local state changes run in short transactions; no transaction is held
 * open across the remote calls.
 *
 * Known limits (deliberate, see docs/order-service.md): a compensation that itself fails is logged for
 * manual reconciliation rather than retried by a background job, and an order left PENDING by a crash
 * mid-saga is not yet swept by a recovery job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSaga {

    public record PricedLine(UUID productId, BigDecimal unitPrice, int quantity) {
    }

    private static final String PAYMENT_DECLINED = "DECLINED";

    private final TransactionTemplate transactionTemplate;
    private final CustomerOrderRepository orderRepository;
    private final OutboxService outboxService;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderResponse execute(String customerEmail, List<PricedLine> lines) {
        BigDecimal total = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        UUID orderId = createPendingOrder(customerEmail, total, lines);
        List<StockLine> stock = lines.stream()
                .map(line -> new StockLine(line.productId(), line.quantity()))
                .toList();

        try {
            inventoryClient.reserve(new ReserveStockRequest(orderId, stock));
        } catch (FeignException ex) {
            RuntimeException failure = failure(ex,
                    "Insufficient stock for one or more items",
                    "Inventory service is unavailable, please retry");
            cancel(orderId, failure.getMessage());
            throw failure;
        }

        PaymentView payment;
        try {
            payment = paymentClient.charge(new ChargePaymentRequest(orderId, total)).data();
        } catch (FeignException ex) {
            releaseStock(orderId);
            RuntimeException failure = new ServiceUnavailableException("Payment service is unavailable, please retry");
            cancel(orderId, failure.getMessage());
            throw failure;
        }
        if (payment == null || PAYMENT_DECLINED.equals(payment.status())) {
            releaseStock(orderId);
            String reason = payment == null ? "Payment declined" : "Payment declined: " + payment.declineReason();
            cancel(orderId, reason);
            throw new ConflictException(reason);
        }

        try {
            inventoryClient.commit(orderId);
        } catch (FeignException ex) {
            refundPayment(orderId);
            releaseStock(orderId);
            RuntimeException failure = new ServiceUnavailableException("Order could not be completed, please retry");
            cancel(orderId, "Stock commit failed; payment refunded");
            throw failure;
        }

        return confirm(orderId);
    }

    private UUID createPendingOrder(String customerEmail, BigDecimal total, List<PricedLine> lines) {
        return transactionTemplate.execute(status -> {
            CustomerOrder order = new CustomerOrder(customerEmail, total);
            lines.forEach(line -> order.addItem(line.productId(), line.unitPrice(), line.quantity()));
            return orderRepository.save(order).getId();
        });
    }

    private OrderResponse confirm(UUID orderId) {
        return transactionTemplate.execute(status -> {
            CustomerOrder order = findOrder(orderId);
            order.confirm();
            outboxService.enqueue(OrderEvent.of(OrderEvent.ORDER_CONFIRMED, order));
            return OrderResponse.from(order);
        });
    }

    private void cancel(UUID orderId, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            CustomerOrder order = findOrder(orderId);
            order.cancel(reason);
            outboxService.enqueue(OrderEvent.of(OrderEvent.ORDER_CANCELLED, order));
        });
    }

    private void releaseStock(UUID orderId) {
        try {
            inventoryClient.release(orderId);
        } catch (FeignException ex) {
            log.error("Failed to release stock for order {}; manual reconciliation required", orderId, ex);
        }
    }

    private void refundPayment(UUID orderId) {
        try {
            paymentClient.refund(orderId);
        } catch (FeignException ex) {
            log.error("Failed to refund payment for order {}; manual reconciliation required", orderId, ex);
        }
    }

    private CustomerOrder findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    /** A 409 from a downstream service means the business rule rejected the request; anything else is an outage. */
    private RuntimeException failure(FeignException ex, String conflictMessage, String unavailableMessage) {
        return ex.status() == 409
                ? new ConflictException(conflictMessage)
                : new ServiceUnavailableException(unavailableMessage);
    }
}
