package com.orderflow.orderservice.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.PlaceOrderRequest;
import com.orderflow.orderservice.service.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Returns 201 only for a confirmed order; a failed saga surfaces as 409 or 503 with the reason. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request,
                                            Authentication authentication) {
        return ApiResponse.success(orderService.place(request, authentication.getName()), "Order placed");
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable("id") UUID id, Authentication authentication) {
        return ApiResponse.success(
                orderService.getOrder(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping
    public ApiResponse<Page<OrderResponse>> listMyOrders(Authentication authentication, Pageable pageable) {
        return ApiResponse.success(orderService.listOrders(authentication.getName(), pageable));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
