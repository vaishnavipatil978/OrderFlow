package com.orderflow.notificationservice.service;

import com.orderflow.notificationservice.entity.Notification;
import com.orderflow.notificationservice.messaging.OrderEvent;
import com.orderflow.notificationservice.repository.NotificationRepository;
import com.orderflow.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository);
    }

    @Test
    void confirmedOrderProducesAnEmailNotification() {
        OrderEvent event = event(OrderEvent.ORDER_CONFIRMED, null);
        when(notificationRepository.existsByEventId(event.eventId())).thenReturn(false);

        notificationService.handle(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getRecipient()).isEqualTo("jane@orderflow.dev");
        assertThat(saved.getValue().getSubject()).isEqualTo("Your order is confirmed");
        assertThat(saved.getValue().getStatus()).isEqualTo("SENT");
    }

    @Test
    void cancelledOrderIncludesTheReason() {
        OrderEvent event = event(OrderEvent.ORDER_CANCELLED, "Insufficient stock");
        when(notificationRepository.existsByEventId(event.eventId())).thenReturn(false);

        notificationService.handle(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getBody()).contains("Insufficient stock");
    }

    @Test
    void redeliveredEventIsSkipped() {
        OrderEvent event = event(OrderEvent.ORDER_CONFIRMED, null);
        when(notificationRepository.existsByEventId(event.eventId())).thenReturn(true);

        notificationService.handle(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void unknownEventTypeIsIgnored() {
        OrderEvent event = event("SOMETHING_ELSE", null);
        when(notificationRepository.existsByEventId(event.eventId())).thenReturn(false);

        notificationService.handle(event);

        verify(notificationRepository, never()).save(any());
    }

    private OrderEvent event(String type, String reason) {
        return new OrderEvent(UUID.randomUUID(), type, UUID.randomUUID(), "jane@orderflow.dev",
                new BigDecimal("42.00"), "CONFIRMED", reason, Instant.now());
    }
}
