package com.orderflow.orderservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * FOR UPDATE SKIP LOCKED lets several order-service instances relay the outbox concurrently
     * without publishing the same row twice in the common case (the consumer-side dedup by event
     * id covers the rare crash-between-send-and-commit window).
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published = FALSE
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch();
}
