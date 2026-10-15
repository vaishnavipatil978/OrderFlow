package com.orderflow.orderservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.orderservice.messaging.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /** MANDATORY: enqueueing outside the order's transaction would defeat the outbox guarantee. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(OrderEvent event) {
        outboxEventRepository.save(new OutboxEvent(
                UUID.randomUUID(), event.orderId(), event.eventType(), OrderEvent.TOPIC, serialize(event)));
    }

    private String serialize(OrderEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize order event " + event.eventId(), ex);
        }
    }
}
