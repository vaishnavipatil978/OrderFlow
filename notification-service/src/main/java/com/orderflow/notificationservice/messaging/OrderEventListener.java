package com.orderflow.notificationservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.notificationservice.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /** A payload that can't be parsed is not retried - it goes straight to the dead-letter topic. */
    @KafkaListener(topics = "order.events", groupId = "notification-service")
    public void onOrderEvent(String payload) throws JsonProcessingException {
        OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);
        notificationService.handle(event);
    }
}
