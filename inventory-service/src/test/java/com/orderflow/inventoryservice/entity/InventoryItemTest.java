package com.orderflow.inventoryservice.entity;

import com.orderflow.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void reserveReducesAvailability() {
        InventoryItem item = new InventoryItem(UUID.randomUUID(), 10);

        item.reserve(4);

        assertThat(item.getQuantityReserved()).isEqualTo(4);
        assertThat(item.available()).isEqualTo(6);
    }

    @Test
    void reserveBeyondAvailableIsRejected() {
        InventoryItem item = new InventoryItem(UUID.randomUUID(), 3);

        assertThatThrownBy(() -> item.reserve(4)).isInstanceOf(ConflictException.class);
        assertThat(item.getQuantityReserved()).isZero();
    }

    @Test
    void commitConsumesOnHandAndReservation() {
        InventoryItem item = new InventoryItem(UUID.randomUUID(), 10);
        item.reserve(4);

        item.commit(4);

        assertThat(item.getQuantityOnHand()).isEqualTo(6);
        assertThat(item.getQuantityReserved()).isZero();
    }

    @Test
    void releaseRestoresAvailability() {
        InventoryItem item = new InventoryItem(UUID.randomUUID(), 10);
        item.reserve(4);

        item.release(4);

        assertThat(item.available()).isEqualTo(10);
    }

    @Test
    void onHandCannotDropBelowReserved() {
        InventoryItem item = new InventoryItem(UUID.randomUUID(), 10);
        item.reserve(7);

        assertThatThrownBy(() -> item.changeQuantityOnHand(5)).isInstanceOf(ConflictException.class);
    }
}
