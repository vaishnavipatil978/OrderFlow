package com.orderflow.inventoryservice.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.inventoryservice.dto.ReserveStockRequest;
import com.orderflow.inventoryservice.dto.StockLevelResponse;
import com.orderflow.inventoryservice.dto.StockUpdateRequest;
import com.orderflow.inventoryservice.service.interfaces.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Reservation endpoints are called by order-service during the saga, forwarding the customer's
 * token - any authenticated caller can reach them today. Service-to-service identity is a known
 * gap, see docs/inventory-service.md.
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ApiResponse<StockLevelResponse> getStock(@PathVariable("productId") UUID productId) {
        return ApiResponse.success(inventoryService.getStock(productId));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<StockLevelResponse> setStock(@PathVariable("productId") UUID productId,
                                                    @Valid @RequestBody StockUpdateRequest request) {
        return ApiResponse.success(
                inventoryService.setStock(productId, request.quantityOnHand()), "Stock updated");
    }

    @PostMapping("/reservations")
    public ApiResponse<Void> reserve(@Valid @RequestBody ReserveStockRequest request) {
        inventoryService.reserve(request.orderId(), request.items());
        return ApiResponse.success(null, "Stock reserved");
    }

    @PostMapping("/reservations/{orderId}/commit")
    public ApiResponse<Void> commit(@PathVariable("orderId") UUID orderId) {
        inventoryService.commit(orderId);
        return ApiResponse.success(null, "Stock committed");
    }

    @PostMapping("/reservations/{orderId}/release")
    public ApiResponse<Void> release(@PathVariable("orderId") UUID orderId) {
        inventoryService.release(orderId);
        return ApiResponse.success(null, "Stock released");
    }
}
