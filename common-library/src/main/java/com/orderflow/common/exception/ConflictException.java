package com.orderflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A request conflicts with the current state of the resource - e.g. cancelling an
 * order that has already shipped, or reserving stock that is no longer available.
 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
