package com.orderflow.apigateway.fallback;

import com.orderflow.apigateway.filter.CorrelationIdConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Target of every route's CircuitBreaker fallbackUri (config-repo/api-gateway.yml). Gateway's
 * CircuitBreaker filter forwards here - preserving the original HTTP method - whenever the
 * downstream call fails or times out, so no @RequestMapping method restriction is applied.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{service}")
    public Mono<ResponseEntity<GatewayErrorResponse>> fallback(@PathVariable("service") String service, ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GatewayErrorResponse.serviceUnavailable(service, correlationId)));
    }
}
