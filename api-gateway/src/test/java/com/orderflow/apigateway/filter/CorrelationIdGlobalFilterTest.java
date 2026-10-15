package com.orderflow.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    @Test
    void generatesCorrelationIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/identity/whoami"));
        GatewayFilterChain chain = capturingChain();

        filter.filter(exchange, chain).block();

        String responseHeader = exchange.getResponse().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        assertThat(responseHeader).isNotBlank();
    }

    @Test
    void propagatesClientSuppliedCorrelationIdUnchanged() {
        String suppliedId = "client-supplied-id-123";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/identity/whoami")
                        .header(CorrelationIdConstants.HEADER_NAME, suppliedId));
        ServerWebExchange[] downstreamExchange = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            downstreamExchange[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME))
                .isEqualTo(suppliedId);
        assertThat(downstreamExchange[0].getRequest().getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME))
                .isEqualTo(suppliedId);
    }

    private GatewayFilterChain capturingChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }
}
