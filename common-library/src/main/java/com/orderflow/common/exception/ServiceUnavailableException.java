package com.orderflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A downstream dependency needed to complete the request is unreachable or failing. Distinct
 * from ConflictException: the caller's request was fine, retrying later may succeed.
 */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }
}
