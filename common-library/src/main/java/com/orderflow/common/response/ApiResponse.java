package com.orderflow.common.response;

import com.orderflow.common.correlation.CorrelationIdConstants;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

/**
 * Generic API response envelope used by every OrderFlow business service so that
 * clients get one consistent response shape, success or failure. {@code correlationId}
 * is read from MDC (populated by {@link com.orderflow.common.correlation.CorrelationIdFilter})
 * so callers can correlate a response with server-side logs without any extra wiring.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        List<ApiError> errors,
        String correlationId,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Request processed successfully");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, List.of(), currentCorrelationId(), Instant.now());
    }

    public static ApiResponse<Void> error(String message) {
        return error(message, List.of());
    }

    public static ApiResponse<Void> error(String message, List<ApiError> errors) {
        return new ApiResponse<>(false, null, message, errors, currentCorrelationId(), Instant.now());
    }

    private static String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdConstants.MDC_KEY);
        return correlationId != null ? correlationId : "";
    }
}
