package com.orderflow.orderservice.client;

import java.util.List;
import java.util.UUID;

public record ReserveStockRequest(UUID orderId, List<StockLine> items) {
}
