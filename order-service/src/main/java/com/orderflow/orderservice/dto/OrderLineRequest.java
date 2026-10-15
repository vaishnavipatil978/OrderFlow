package com.orderflow.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderLineRequest(

        @NotNull
        UUID productId,

        @Positive
        int quantity
) {
}
