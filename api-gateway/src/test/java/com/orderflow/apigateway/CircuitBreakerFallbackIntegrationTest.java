package com.orderflow.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.orderflow.apigateway.filter.CorrelationIdConstants.HEADER_NAME;

/**
 * End-to-end proof that routing + resilience + correlation IDs are actually wired together,
 * not just declared: a request through the real gateway filter chain to a route whose
 * downstream is unreachable (src/test/resources/application.yml points test-service at
 * http://localhost:1) must be caught by the CircuitBreaker filter, forwarded to
 * FallbackController, and still carry a correlation id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CircuitBreakerFallbackIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void unreachableDownstreamIsCaughtByCircuitBreakerAndFallsBack() {
        webTestClient.get().uri("/api/test/anything")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().exists(HEADER_NAME)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("test-service is currently unavailable. Please try again later.");
    }

    @Test
    void clientSuppliedCorrelationIdSurvivesTheFallbackRoundTrip() {
        String correlationId = "gateway-e2e-test-id";

        webTestClient.get().uri("/api/test/anything")
                .header(HEADER_NAME, correlationId)
                .exchange()
                .expectHeader().valueEquals(HEADER_NAME, correlationId)
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo(correlationId);
    }
}
