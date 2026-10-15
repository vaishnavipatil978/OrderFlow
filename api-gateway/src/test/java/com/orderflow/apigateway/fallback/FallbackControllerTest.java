package com.orderflow.apigateway.fallback;

import com.orderflow.apigateway.filter.CorrelationIdConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

class FallbackControllerTest {

    private final WebTestClient webTestClient = WebTestClient.bindToController(new FallbackController()).build();

    @Test
    void returnsServiceUnavailableWithEnvelope() {
        webTestClient.get().uri("/fallback/identity-service")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("identity-service is currently unavailable. Please try again later.")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void echoesCorrelationIdFromRequest() {
        String correlationId = "test-correlation-id";

        webTestClient.get().uri("/fallback/order-service")
                .header(CorrelationIdConstants.HEADER_NAME, correlationId)
                .exchange()
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo(correlationId);
    }
}
