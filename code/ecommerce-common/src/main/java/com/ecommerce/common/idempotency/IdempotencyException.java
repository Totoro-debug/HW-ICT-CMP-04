package com.ecommerce.common.idempotency;

import com.ecommerce.common.exception.ConflictException;

public class IdempotencyException extends ConflictException {

    public IdempotencyException(String message) {
        super(message);
    }
}
