package com.ecommerce.common.event;

/**
 * Marker interface for event handlers whose failures must roll back the publisher transaction.
 */
public interface StrongConsistencyEventHandler {
}
