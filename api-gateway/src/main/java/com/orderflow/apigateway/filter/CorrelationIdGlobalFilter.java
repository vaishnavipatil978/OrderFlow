package com.orderflow.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Edge equivalent of common-library's servlet-based CorrelationIdFilter: reads
 * {@code X-Correlation-Id} from the incoming request (generating one if absent), mutates the
 * request so every downstream service sees the same id, and echoes it back on the response -
 * so a call spanning gateway -> service -> service shares one traceable id end to end.
 *
 * Not reused from common-library directly: that module assumes the servlet stack
 * (HttpServletRequest/OncePerRequestFilter), while api-gateway is reactive/WebFlux.
 *
 * The MDC put/remove here only covers the single synchronous log statement below, not the
 * request's full downstream processing - WebFlux hops threads across the reactive chain, so
 * MDC (thread-local) can't follow a request the way it does in the servlet-based services.
 * A real Reactor Context <-> MDC bridge is deferred; this still gets the correlation id onto
 * the wire (headers), which is what downstream services need.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CorrelationIdConstants.HEADER_NAME, correlationId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().set(CorrelationIdConstants.HEADER_NAME, correlationId);

        MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);
        try {
            log.debug("Routing {} {} correlationId={}", request.getMethod(), request.getURI(), correlationId);
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
