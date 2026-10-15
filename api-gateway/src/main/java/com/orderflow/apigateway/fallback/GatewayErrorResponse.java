package com.orderflow.apigateway.fallback;

import java.time.Instant;

/**
 * Minimal error envelope for responses the gateway itself generates (as opposed to
 * proxying a downstream response) - currently just circuit-breaker fallbacks. Deliberately
 * not common-library's ApiResponse: that type isn't reusable here (api-gateway is
 * WebFlux/reactive, common-library targets the servlet stack), and this needs far less
 * shape (no data/errors payload) since it only ever represents "downstream unavailable".
 */
public record GatewayErrorResponse(boolean success, String message, String correlationId, Instant timestamp) {

    public static GatewayErrorResponse serviceUnavailable(String serviceName, String correlationId) {
        return new GatewayErrorResponse(
                false,
                serviceName + " is currently unavailable. Please try again later.",
                correlationId,
                Instant.now());
    }
}
