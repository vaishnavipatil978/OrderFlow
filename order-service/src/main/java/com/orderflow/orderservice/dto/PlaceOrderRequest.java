package com.orderflow.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(

        @NotEmpty @Valid
        List<OrderLineRequest> items
) {
}
