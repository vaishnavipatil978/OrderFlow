package com.orderflow.common.correlation;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Propagates the current request's correlation ID onto outgoing OpenFeign calls so a
 * saga spanning order-service -> product-service -> inventory-service -> payment-service
 * shares one correlation ID end to end.
 */
public class FeignCorrelationIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        if (StringUtils.hasText(correlationId)) {
            template.header(CorrelationIdConstants.HEADER_NAME, correlationId);
        }
    }
}
