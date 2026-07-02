package com.ecommerce.common.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all domain events in the ShopHub system.
 * Extends Spring's ApplicationEvent for integration with the Spring event bus.
 */
public abstract class AbstractDomainEvent extends ApplicationEvent {

    private final String eventId;
    private final String eventType;
    private final LocalDateTime occurredAt;
    private final String aggregateId;
    private final String traceId;

    public AbstractDomainEvent(Object source) {
        this(source, null, null, null);
    }

    protected AbstractDomainEvent(Object source, String eventType, String aggregateId, String traceId) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType != null ? eventType : getClass().getSimpleName();
        this.occurredAt = LocalDateTime.now();
        this.aggregateId = aggregateId;
        this.traceId = traceId != null ? traceId : this.eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTraceId() {
        return traceId;
    }
}
