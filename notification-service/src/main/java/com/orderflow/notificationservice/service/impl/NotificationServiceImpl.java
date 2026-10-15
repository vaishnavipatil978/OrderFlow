package com.orderflow.notificationservice.service.impl;

import com.orderflow.notificationservice.dto.NotificationResponse;
import com.orderflow.notificationservice.entity.Notification;
import com.orderflow.notificationservice.messaging.OrderEvent;
import com.orderflow.notificationservice.repository.NotificationRepository;
import com.orderflow.notificationservice.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String CHANNEL_EMAIL = "EMAIL";

    private final NotificationRepository notificationRepository;

    /**
     * Idempotent per event id: Kafka delivers at least once, so a redelivered event is skipped
     * rather than producing a second message to the customer.
     */
    @Override
    @Transactional
    public void handle(OrderEvent event) {
        if (notificationRepository.existsByEventId(event.eventId())) {
            log.debug("Skipping already-processed event {}", event.eventId());
            return;
        }
        String subject;
        String body;
        switch (event.eventType()) {
            case OrderEvent.ORDER_CONFIRMED -> {
                subject = "Your order is confirmed";
                body = "Order %s is confirmed. Total charged: %s.".formatted(event.orderId(), event.totalAmount());
            }
            case OrderEvent.ORDER_CANCELLED -> {
                subject = "Your order was cancelled";
                body = "Order %s was cancelled: %s.".formatted(event.orderId(), event.reason());
            }
            default -> {
                log.info("Ignoring unsupported order event type {}", event.eventType());
                return;
            }
        }
        // Simulated delivery: a real provider adapter would replace this log line.
        log.info("[SIMULATED EMAIL] to={} subject=\"{}\" body=\"{}\"", event.customerEmail(), subject, body);
        notificationRepository.save(new Notification(
                event.eventId(), event.orderId(), event.customerEmail(), CHANNEL_EMAIL, subject, body));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForRecipient(String recipient, Pageable pageable) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient, pageable)
                .map(NotificationResponse::from);
    }
}
