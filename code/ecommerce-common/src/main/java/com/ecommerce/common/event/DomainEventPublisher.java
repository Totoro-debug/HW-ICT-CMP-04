package com.ecommerce.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Central publisher for domain events.
 * Wraps Spring's ApplicationEventPublisher and provides uniform publish methods.
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                FailedEventRecordRepository failedEventRecordRepository,
                                ObjectMapper objectMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a non-strong-consistency domain event. Listener exceptions are
     * persisted and swallowed so that non-critical listeners do not abort the
     * main business transaction.
     */
    public void publish(AbstractDomainEvent event) {
        publish(event, false);
    }

    /**
     * Publishes a strong-consistency domain event. Listener exceptions are
     * persisted and rethrown to allow the caller transaction to roll back.
     */
    public void publishStrong(AbstractDomainEvent event) {
        publish(event, true);
    }

    public void publish(AbstractDomainEvent event, boolean strongConsistency) {
        log.info("Publishing domain event: eventId={}, type={}, occurredAt={}, strongConsistency={}",
                event.getEventId(), event.getClass().getSimpleName(), event.getOccurredAt(), strongConsistency);
        try {
            applicationEventPublisher.publishEvent(event);
            log.info("Domain event published successfully: eventId={}", event.getEventId());
        } catch (RuntimeException e) {
            log.error("Failed to publish domain event: eventId={}, type={}, error={}",
                    event.getEventId(), event.getClass().getSimpleName(), e.getMessage(), e);
            persistFailure(event, e);
            if (strongConsistency) {
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to publish domain event: eventId={}, type={}, error={}",
                    event.getEventId(), event.getClass().getSimpleName(), e.getMessage(), e);
            persistFailure(event, e);
            if (strongConsistency) {
                throw new IllegalStateException("Strong-consistency event listener failed", e);
            }
        }
    }

    private void persistFailure(AbstractDomainEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(event.getClass().getSimpleName());
            record.setEventPayload(serializeEvent(event));
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to persist failed event record: {}", e.getMessage(), e);
        }
    }

    private String serializeEvent(AbstractDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize domain event: eventId={}, type={}",
                    event.getEventId(), event.getClass().getSimpleName(), e);
            return "{}";
        }
    }
}
