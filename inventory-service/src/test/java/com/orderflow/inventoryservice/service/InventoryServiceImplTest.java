package com.orderflow.inventoryservice.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.inventoryservice.dto.StockLine;
import com.orderflow.inventoryservice.entity.InventoryItem;
import com.orderflow.inventoryservice.entity.StockReservation;
import com.orderflow.inventoryservice.repository.InventoryItemRepository;
import com.orderflow.inventoryservice.repository.StockReservationRepository;
import com.orderflow.inventoryservice.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private StockReservationRepository reservationRepository;

    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(itemRepository, reservationRepository);
    }

    @Test
    void reserveIsIdempotentForAnOrderThatAlreadyHasReservations() {
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.existsByOrderId(orderId)).thenReturn(true);

        inventoryService.reserve(orderId, List.of(new StockLine(UUID.randomUUID(), 2)));

        verify(itemRepository, never()).findForUpdate(any());
    }

    @Test
    void reserveFailsWithoutCreatingReservationWhenStockIsInsufficient() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(reservationRepository.existsByOrderId(orderId)).thenReturn(false);
        when(itemRepository.findForUpdate(productId)).thenReturn(Optional.of(new InventoryItem(productId, 1)));

        assertThatThrownBy(() -> inventoryService.reserve(orderId, List.of(new StockLine(productId, 5))))
                .isInstanceOf(ConflictException.class);
        verify(reservationRepository, never()).save(any(StockReservation.class));
    }

    @Test
    void reserveCreatesReservationWhenStockIsAvailable() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(reservationRepository.existsByOrderId(orderId)).thenReturn(false);
        when(itemRepository.findForUpdate(productId)).thenReturn(Optional.of(new InventoryItem(productId, 10)));

        inventoryService.reserve(orderId, List.of(new StockLine(productId, 3)));

        verify(reservationRepository).save(any(StockReservation.class));
    }

    @Test
    void commitForUnknownOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of());

        assertThatThrownBy(() -> inventoryService.commit(orderId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void releaseForUnknownOrderIsANoOp() {
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of());

        assertThatCode(() -> inventoryService.release(orderId)).doesNotThrowAnyException();
    }
}
