package com.orderflow.orderservice.client;

import com.orderflow.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "PAYMENT-SERVICE")
public interface PaymentClient {

    @PostMapping("/payments/charge")
    ApiResponse<PaymentView> charge(@RequestBody ChargePaymentRequest request);

    @PostMapping("/payments/{orderId}/refund")
    ApiResponse<PaymentView> refund(@PathVariable("orderId") UUID orderId);
}
