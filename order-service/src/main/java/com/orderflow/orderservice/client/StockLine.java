package com.orderflow.orderservice.client;

import java.util.UUID;

public record StockLine(UUID productId, int quantity) {
}
