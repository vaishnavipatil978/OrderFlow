package com.orderflow.orderservice.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.common.exception.ValidationException;
import com.orderflow.common.response.ApiResponse;
import com.orderflow.orderservice.FeignTestErrors;
import com.orderflow.orderservice.client.ProductClient;
import com.orderflow.orderservice.client.ProductView;
import com.orderflow.orderservice.dto.OrderLineRequest;
import com.orderflow.orderservice.dto.PlaceOrderRequest;
import com.orderflow.orderservice.entity.CustomerOrder;
import com.orderflow.orderservice.repository.CustomerOrderRepository;
import com.orderflow.orderservice.saga.OrderSaga;
import com.orderflow.orderservice.saga.OrderSaga.PricedLine;
import com.orderflow.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private ProductClient productClient;
    @Mock
    private OrderSaga orderSaga;
    @Mock
    private CustomerOrderRepository orderRepository;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(productClient, orderSaga, orderRepository);
    }

    @Test
    void placePricesEachLineFromProductServiceAndDelegatesToTheSaga() {
        UUID productId = UUID.randomUUID();
        when(productClient.getById(productId))
                .thenReturn(ApiResponse.success(new ProductView(productId, "Widget", new BigDecimal("2.50"), true)));
        when(orderSaga.execute(anyString(), anyList())).thenReturn(null);

        orderService.place(new PlaceOrderRequest(List.of(new OrderLineRequest(productId, 3))), "jane@orderflow.dev");

        verify(orderSaga).execute(eq("jane@orderflow.dev"),
                eq(List.of(new PricedLine(productId, new BigDecimal("2.50"), 3))));
    }

    @Test
    void placeRejectsTheSameProductTwice() {
        UUID productId = UUID.randomUUID();
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(
                new OrderLineRequest(productId, 1), new OrderLineRequest(productId, 2)));

        assertThatThrownBy(() -> orderService.place(request, "jane@orderflow.dev"))
                .isInstanceOf(ValidationException.class);
        verify(orderSaga, never()).execute(anyString(), anyList());
    }

    @Test
    void placeRejectsAnInactiveProduct() {
        UUID productId = UUID.randomUUID();
        when(productClient.getById(productId))
                .thenReturn(ApiResponse.success(new ProductView(productId, "Old", BigDecimal.TEN, false)));

        assertThatThrownBy(() -> orderService.place(
                new PlaceOrderRequest(List.of(new OrderLineRequest(productId, 1))), "jane@orderflow.dev"))
                .isInstanceOf(ConflictException.class);
        verify(orderSaga, never()).execute(anyString(), anyList());
    }

    @Test
    void placeMapsAnUnknownProductToNotFound() {
        UUID productId = UUID.randomUUID();
        when(productClient.getById(productId)).thenThrow(FeignTestErrors.status(404));

        assertThatThrownBy(() -> orderService.place(
                new PlaceOrderRequest(List.of(new OrderLineRequest(productId, 1))), "jane@orderflow.dev"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void customersCannotReadAnotherCustomersOrder() {
        CustomerOrder order = new CustomerOrder("owner@orderflow.dev", BigDecimal.TEN);
        when(orderRepository.findById(any(UUID.class))).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(UUID.randomUUID(), "intruder@orderflow.dev", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminsCanReadAnyOrder() {
        CustomerOrder order = new CustomerOrder("owner@orderflow.dev", BigDecimal.TEN);
        when(orderRepository.findById(any(UUID.class))).thenReturn(Optional.of(order));

        assertThat(orderService.getOrder(UUID.randomUUID(), "admin@orderflow.dev", true).customerEmail())
                .isEqualTo("owner@orderflow.dev");
    }
}
