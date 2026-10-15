package com.orderflow.orderservice.service.interfaces;

import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.PlaceOrderRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponse place(PlaceOrderRequest request, String customerEmail);

    OrderResponse getOrder(UUID orderId, String requesterEmail, boolean admin);

    Page<OrderResponse> listOrders(String customerEmail, Pageable pageable);
}
