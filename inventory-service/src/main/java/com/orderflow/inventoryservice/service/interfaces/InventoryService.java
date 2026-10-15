package com.orderflow.inventoryservice.service.interfaces;

import com.orderflow.inventoryservice.dto.StockLevelResponse;
import com.orderflow.inventoryservice.dto.StockLine;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    StockLevelResponse getStock(UUID productId);

    StockLevelResponse setStock(UUID productId, int quantityOnHand);

    void reserve(UUID orderId, List<StockLine> items);

    void commit(UUID orderId);

    void release(UUID orderId);
}
