package com.ecommerce.common.event;

public class FailedEventReplayResult {

    private final Long recordId;
    private final FailedEventStatus status;
    private final int retryCount;
    private final String errorMessage;

    public FailedEventReplayResult(Long recordId, FailedEventStatus status, int retryCount, String errorMessage) {
        this.recordId = recordId;
        this.status = status;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
    }

    public Long getRecordId() { return recordId; }
    public FailedEventStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public String getErrorMessage() { return errorMessage; }
}
