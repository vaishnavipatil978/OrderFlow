package com.orderflow.inventoryservice.entity;

import com.orderflow.common.entity.BaseEntity;
import com.orderflow.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "inventory_items")
@NoArgsConstructor
public class InventoryItem extends BaseEntity {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    public InventoryItem(UUID productId, int quantityOnHand) {
        this.productId = productId;
        this.quantityOnHand = quantityOnHand;
    }

    public int available() {
        return quantityOnHand - quantityReserved;
    }

    public void changeQuantityOnHand(int newQuantity) {
        if (newQuantity < quantityReserved) {
            throw new ConflictException(
                    "Quantity on hand cannot drop below the %d units currently reserved".formatted(quantityReserved));
        }
        this.quantityOnHand = newQuantity;
    }

    public void reserve(int quantity) {
        if (quantity > available()) {
            throw new ConflictException("Insufficient stock for product %s: requested %d, available %d"
                    .formatted(productId, quantity, available()));
        }
        this.quantityReserved += quantity;
    }

    public void release(int quantity) {
        this.quantityReserved -= quantity;
    }

    public void commit(int quantity) {
        this.quantityOnHand -= quantity;
        this.quantityReserved -= quantity;
    }
}
