package com.orderflow.orderservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row: written in the same database transaction as the order state change it
 * describes, then relayed to Kafka by {@link OutboxPublisher}. This is what keeps "order confirmed"
 * and "event announced" from ever disagreeing, even if Kafka is down when the order commits.
 */
@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(nullable = false, length = 128, updatable = false)
    private String topic;

    @Column(nullable = false, length = 4000, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent(UUID id, UUID aggregateId, String eventType, String topic, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.published = false;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }
}
