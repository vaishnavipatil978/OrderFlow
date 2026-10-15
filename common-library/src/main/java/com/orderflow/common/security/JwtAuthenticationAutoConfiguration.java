package com.orderflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;

/**
 * Gated on both jjwt-api (Jwts) and spring-security-core (AccessDeniedException) being on the
 * classpath - both are <optional> in this module's own pom.xml, so this only activates for a
 * service that has deliberately opted in by adding its own jjwt-api/jjwt-impl/jjwt-jackson +
 * spring-boot-starter-security dependencies (identity-service today; product-service and
 * others as they need to know who's calling them). A service that hasn't opted in never sees
 * these beans at all, the same way JpaAuditingAutoConfiguration and
 * FeignCorrelationIdAutoConfiguration stay inert for services that don't use JPA/Feign.
 */
@AutoConfiguration
@ConditionalOnClass({Jwts.class, AccessDeniedException.class})
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAuthenticationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new JwtAuthenticationEntryPoint(objectMapper);
    }
}
