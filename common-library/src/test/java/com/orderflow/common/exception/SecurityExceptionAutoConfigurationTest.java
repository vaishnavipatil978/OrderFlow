package com.orderflow.common.exception;

import com.orderflow.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionAutoConfigurationTest {

    private final SecurityExceptionAutoConfiguration handler = new SecurityExceptionAutoConfiguration();

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void authenticationExceptionMapsTo401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAuthenticationException(new BadCredentialsException("bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().success()).isFalse();
    }
}
