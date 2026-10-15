package com.orderflow.orderservice.client;

import java.math.BigDecimal;
import java.util.UUID;

/** Subset of product-service's ProductResponse this service depends on. */
public record ProductView(UUID id, String name, BigDecimal price, boolean active) {
}
