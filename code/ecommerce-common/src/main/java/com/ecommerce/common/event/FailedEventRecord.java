package com.ecommerce.common.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Records a domain event that failed during publishing or listener processing.
 *
 * <p>Failed records are persisted by the event publisher so that failures can
 * be inspected and replayed through administrative workflows.
 */
@Entity
@Table(name = "failed_event_records")
public class FailedEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Lob
    @Column(name = "event_payload")
    private String eventPayload;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "retried")
    private boolean retried;

    @Column(name = "retry_count")
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FailedEventStatus status = FailedEventStatus.PENDING;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    public FailedEventRecord() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventPayload() { return eventPayload; }
    public void setEventPayload(String eventPayload) { this.eventPayload = eventPayload; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public boolean isRetried() { return retried; }
    public void setRetried(boolean retried) { this.retried = retried; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public FailedEventStatus getStatus() { return status; }
    public void setStatus(FailedEventStatus status) { this.status = status; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getReplayedAt() { return replayedAt; }
    public void setReplayedAt(LocalDateTime replayedAt) { this.replayedAt = replayedAt; }
}
