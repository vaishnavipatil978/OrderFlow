package com.orderflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * For programmatic business-rule validation failures that don't map to a single bean
 * validation constraint (those are handled directly via {@code MethodArgumentNotValidException}).
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
