package com.ecommerce.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an idempotent business method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** Business type, for example CREATE_ORDER or PAYMENT_CALLBACK. */
    String businessType();

    /** SpEL expression used to build the idempotency key. */
    String key();

    /** Retention time in seconds for completed records. */
    long ttlSeconds() default 86_400;
}
