package com.orderflow.notificationservice.entity;

import com.orderflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Id of the order event this notification answers - unique, so redelivery never sends twice. */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Column(nullable = false, length = 16, updatable = false)
    private String channel;

    @Column(nullable = false, length = 255, updatable = false)
    private String subject;

    @Column(nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(nullable = false, length = 16)
    private String status;

    public Notification(UUID eventId, UUID orderId, String recipient, String channel, String subject, String body) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.recipient = recipient;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.status = "SENT";
    }
}
