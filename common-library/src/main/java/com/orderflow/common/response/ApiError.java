package com.orderflow.common.response;

/**
 * A single error item within an {@link ApiResponse}. {@code field} is null for
 * errors not tied to a specific request field (e.g. a business rule violation).
 */
public record ApiError(String field, String message) {

    public static ApiError of(String message) {
        return new ApiError(null, message);
    }
}
