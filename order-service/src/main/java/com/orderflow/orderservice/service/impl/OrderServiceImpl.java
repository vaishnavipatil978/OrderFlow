package com.orderflow.orderservice.service.impl;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.common.exception.ServiceUnavailableException;
import com.orderflow.common.exception.ValidationException;
import com.orderflow.orderservice.client.ProductClient;
import com.orderflow.orderservice.client.ProductView;
import com.orderflow.orderservice.dto.OrderLineRequest;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.PlaceOrderRequest;
import com.orderflow.orderservice.entity.CustomerOrder;
import com.orderflow.orderservice.repository.CustomerOrderRepository;
import com.orderflow.orderservice.saga.OrderSaga;
import com.orderflow.orderservice.saga.OrderSaga.PricedLine;
import com.orderflow.orderservice.service.interfaces.OrderService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ProductClient productClient;
    private final OrderSaga orderSaga;
    private final CustomerOrderRepository orderRepository;

    @Override
    public OrderResponse place(PlaceOrderRequest request, String customerEmail) {
        Set<UUID> distinctProducts = new HashSet<>();
        for (OrderLineRequest line : request.items()) {
            if (!distinctProducts.add(line.productId())) {
                throw new ValidationException("Product " + line.productId() + " appears more than once in the order");
            }
        }
        List<PricedLine> lines = request.items().stream().map(this::priceLine).toList();
        return orderSaga.execute(customerEmail, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, String requesterEmail, boolean admin) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        // Someone else's order looks identical to a missing one, so ids can't be probed.
        if (!admin && !order.getCustomerEmail().equals(requesterEmail)) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return OrderResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(String customerEmail, Pageable pageable) {
        return orderRepository.findByCustomerEmail(customerEmail, pageable).map(OrderResponse::from);
    }

    private PricedLine priceLine(OrderLineRequest line) {
        ProductView product;
        try {
            product = productClient.getById(line.productId()).data();
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new ResourceNotFoundException("Product", line.productId());
            }
            throw new ServiceUnavailableException("Product service is unavailable, please retry");
        }
        if (product == null || !product.active()) {
            throw new ConflictException("Product %s is not available for purchase".formatted(line.productId()));
        }
        return new PricedLine(line.productId(), product.price(), line.quantity());
    }
}
