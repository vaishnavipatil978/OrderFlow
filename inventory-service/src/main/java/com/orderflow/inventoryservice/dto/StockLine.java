package com.orderflow.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record StockLine(

        @NotNull
        UUID productId,

        @Positive
        int quantity
) {
}
