package com.ecommerce.common.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event listener as strong-consistency processing.
 * Failures from strong-consistency listeners must be propagated by the caller.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongConsistencyEventListener {
}
