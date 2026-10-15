package com.orderflow.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for all expected, handled business-rule failures. Unlike an unexpected
 * {@link Exception}, throwing a {@code BusinessException} is a normal control-flow
 * outcome that {@link GlobalExceptionHandler} maps to a specific HTTP status and
 * error code rather than a generic 500.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
