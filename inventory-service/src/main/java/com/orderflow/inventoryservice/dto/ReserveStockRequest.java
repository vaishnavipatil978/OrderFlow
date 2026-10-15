package com.orderflow.inventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReserveStockRequest(

        @NotNull
        UUID orderId,

        @NotEmpty @Valid
        List<StockLine> items
) {
}
