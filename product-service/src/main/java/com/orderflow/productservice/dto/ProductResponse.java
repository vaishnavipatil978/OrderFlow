package com.orderflow.productservice.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Serializable because it is stored in the Redis product cache. */
public record ProductResponse(
        UUID id,
        String name,
        String description,
        String sku,
        BigDecimal price,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
