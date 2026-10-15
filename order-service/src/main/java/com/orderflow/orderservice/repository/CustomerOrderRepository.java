package com.orderflow.orderservice.repository;

import com.orderflow.orderservice.entity.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Page<CustomerOrder> findByCustomerEmail(String customerEmail, Pageable pageable);
}
