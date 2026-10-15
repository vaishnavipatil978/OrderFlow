package com.orderflow.common.correlation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class FeignCorrelationIdAutoConfiguration {

    @Bean
    public FeignCorrelationIdInterceptor feignCorrelationIdInterceptor() {
        return new FeignCorrelationIdInterceptor();
    }
}
