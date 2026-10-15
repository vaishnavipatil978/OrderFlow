package com.orderflow.orderservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Keyed by order id so all events for one order land on one partition, preserving their order.
     * A send failure stops the batch so later events never overtake earlier ones; already-acknowledged
     * sends in the batch are still marked published when the transaction commits.
     */
    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outboxEventRepository.lockUnpublishedBatch()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception ex) {
                log.warn("Outbox event {} not published yet, will retry: {}", event.getId(), ex.getMessage());
                return;
            }
        }
    }
}
