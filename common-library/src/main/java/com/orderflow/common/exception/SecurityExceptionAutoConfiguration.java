package com.orderflow.common.exception;

import com.orderflow.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Spring Security exception handling, split out from GlobalExceptionHandler and gated on
 * AccessDeniedException actually being on the classpath - spring-security-core is <optional>
 * in this module's own pom.xml, so only services that add Spring Security themselves
 * (identity-service; any later service validating JWTs) have it. Without this guard, a
 * handler referencing these types directly in the always-on GlobalExceptionHandler would
 * throw NoClassDefFoundError the moment a service *without* Spring Security boots a web
 * context and Spring reflects over @RestControllerAdvice method signatures.
 */
@AutoConfiguration
@ConditionalOnClass(AccessDeniedException.class)
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionAutoConfiguration.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied", List.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication failed", List.of()));
    }
}
