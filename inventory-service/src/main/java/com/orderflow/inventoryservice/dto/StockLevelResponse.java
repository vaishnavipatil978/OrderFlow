package com.orderflow.inventoryservice.dto;

import com.orderflow.inventoryservice.entity.InventoryItem;

import java.util.UUID;

public record StockLevelResponse(UUID productId, int quantityOnHand, int quantityReserved, int available) {

    public static StockLevelResponse from(InventoryItem item) {
        return new StockLevelResponse(
                item.getProductId(), item.getQuantityOnHand(), item.getQuantityReserved(), item.available());
    }
}
