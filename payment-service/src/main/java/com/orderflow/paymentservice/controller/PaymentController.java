package com.orderflow.paymentservice.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.paymentservice.dto.ChargeRequest;
import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.service.interfaces.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A declined charge is a normal outcome, not an error: it returns 200 with status DECLINED so the
 * caller (order-service's saga) can branch on it and compensate. Infrastructure failures surface as
 * non-2xx responses instead.
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    public ApiResponse<PaymentResponse> charge(@Valid @RequestBody ChargeRequest request) {
        return ApiResponse.success(paymentService.charge(request), "Payment processed");
    }

    @PostMapping("/{orderId}/refund")
    public ApiResponse<PaymentResponse> refund(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(paymentService.refund(orderId), "Payment refunded");
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> getByOrderId(@PathVariable("orderId") UUID orderId) {
        return ApiResponse.success(paymentService.getByOrderId(orderId));
    }
}
