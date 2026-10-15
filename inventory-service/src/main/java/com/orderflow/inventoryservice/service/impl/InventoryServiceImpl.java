package com.orderflow.inventoryservice.service.impl;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.inventoryservice.dto.StockLevelResponse;
import com.orderflow.inventoryservice.dto.StockLine;
import com.orderflow.inventoryservice.entity.InventoryItem;
import com.orderflow.inventoryservice.entity.ReservationStatus;
import com.orderflow.inventoryservice.entity.StockReservation;
import com.orderflow.inventoryservice.repository.InventoryItemRepository;
import com.orderflow.inventoryservice.repository.StockReservationRepository;
import com.orderflow.inventoryservice.service.interfaces.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository itemRepository;
    private final StockReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public StockLevelResponse getStock(UUID productId) {
        return itemRepository.findById(productId)
                .map(StockLevelResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
    }

    @Override
    @Transactional
    public StockLevelResponse setStock(UUID productId, int quantityOnHand) {
        InventoryItem item = itemRepository.findForUpdate(productId)
                .orElseGet(() -> new InventoryItem(productId, 0));
        item.changeQuantityOnHand(quantityOnHand);
        return StockLevelResponse.from(itemRepository.save(item));
    }

    /**
     * All-or-nothing: any line that can't be satisfied throws, rolling back every reservation made
     * earlier in the same call. Idempotent per order - a retried call for an order that already has
     * reservations is a no-op rather than a double reservation.
     */
    @Override
    @Transactional
    public void reserve(UUID orderId, List<StockLine> items) {
        if (reservationRepository.existsByOrderId(orderId)) {
            return;
        }
        List<StockLine> ordered = items.stream()
                .sorted(Comparator.comparing(StockLine::productId))
                .toList();
        for (StockLine line : ordered) {
            InventoryItem item = findItemForUpdate(line.productId());
            item.reserve(line.quantity());
            reservationRepository.save(new StockReservation(orderId, line.productId(), line.quantity()));
        }
    }

    @Override
    @Transactional
    public void commit(UUID orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new ResourceNotFoundException("Stock reservation for order", orderId);
        }
        for (StockReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                throw new ConflictException("Stock reservation for order %s was already released".formatted(orderId));
            }
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                findItemForUpdate(reservation.getProductId()).commit(reservation.getQuantity());
                reservation.markCommitted();
            }
        }
    }

    /** Releasing an unknown or already-released reservation is a no-op, so compensation can always be retried. */
    @Override
    @Transactional
    public void release(UUID orderId) {
        for (StockReservation reservation : reservationRepository.findByOrderId(orderId)) {
            if (reservation.getStatus() == ReservationStatus.RESERVED) {
                itemRepository.findForUpdate(reservation.getProductId())
                        .ifPresent(item -> item.release(reservation.getQuantity()));
                reservation.markReleased();
            }
        }
    }

    private InventoryItem findItemForUpdate(UUID productId) {
        return itemRepository.findForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
    }
}
