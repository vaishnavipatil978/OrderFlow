package com.orderflow.orderservice.client;

import com.orderflow.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "INVENTORY-SERVICE")
public interface InventoryClient {

    @PostMapping("/inventory/reservations")
    ApiResponse<Void> reserve(@RequestBody ReserveStockRequest request);

    @PostMapping("/inventory/reservations/{orderId}/commit")
    ApiResponse<Void> commit(@PathVariable("orderId") UUID orderId);

    @PostMapping("/inventory/reservations/{orderId}/release")
    ApiResponse<Void> release(@PathVariable("orderId") UUID orderId);
}
