package com.orderflow.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpdateRequest(

        @NotNull @PositiveOrZero
        Integer quantityOnHand
) {
}
