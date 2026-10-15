package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    List<StockReservation> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
