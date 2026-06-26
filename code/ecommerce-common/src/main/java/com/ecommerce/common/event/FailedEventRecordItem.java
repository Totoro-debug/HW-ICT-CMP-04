package com.ecommerce.common.event;

import java.time.LocalDateTime;

/**
 * DTO for exposing failed event records outside the common module.
 */
public class FailedEventRecordItem {

    private final Long id;
    private final String eventType;
    private final String eventPayload;
    private final String errorMessage;
    private final LocalDateTime occurredAt;
    private final boolean retried;
    private final int retryCount;

    public FailedEventRecordItem(Long id, String eventType, String eventPayload, String errorMessage,
                                 LocalDateTime occurredAt, boolean retried, int retryCount) {
        this.id = id;
        this.eventType = eventType;
        this.eventPayload = eventPayload;
        this.errorMessage = errorMessage;
        this.occurredAt = occurredAt;
        this.retried = retried;
        this.retryCount = retryCount;
    }

    public static FailedEventRecordItem from(FailedEventRecord record) {
        return new FailedEventRecordItem(
                record.getId(),
                record.getEventType(),
                record.getEventPayload(),
                record.getErrorMessage(),
                record.getOccurredAt(),
                record.isRetried(),
                record.getRetryCount());
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventPayload() {
        return eventPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public boolean isRetried() {
        return retried;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
