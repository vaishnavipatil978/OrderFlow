package com.orderflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Runs before the request ever reaches a controller, so GlobalExceptionHandler (a
 * @RestControllerAdvice, only invoked during MVC dispatch) never sees a missing/invalid-JWT
 * failure - Spring Security's own ExceptionTranslationFilter calls this directly instead.
 * Reusing ApiResponse here (rather than Spring Security's default plain-text 401) keeps an
 * auth failure in the same envelope shape as every other error response.
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error("Authentication required"));
    }
}
