package com.orderflow.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @NotBlank @Size(max = 64)
        String sku,

        @NotNull @Positive
        BigDecimal price,

        @NotBlank @Size(max = 100)
        String category
) {
}
