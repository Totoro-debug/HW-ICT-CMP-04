package com.ecommerce.user.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.notification.LocalNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Sends registration notifications after the registration transaction commits.
 */
@Component
public class UserRegistrationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationNotificationListener.class);

    private final LocalNotificationService notificationService;
    private final FailedEventRecordRepository failedEventRecordRepository;

    public UserRegistrationNotificationListener(LocalNotificationService notificationService,
                                                FailedEventRecordRepository failedEventRecordRepository) {
        this.notificationService = notificationService;
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistrationNotification(UserRegistrationNotificationEvent event) {
        try {
            notificationService.send(event.getNotificationRequest());
            log.info("User registration notification sent: userId={}, email={}",
                    event.getUserId(), event.getEmail());
        } catch (Exception ex) {
            log.error("User registration notification failed: userId={}, email={}, error={}",
                    event.getUserId(), event.getEmail(), ex.getMessage(), ex);
            persistFailure(event, ex);
        }
    }

    private void persistFailure(UserRegistrationNotificationEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("UserRegistrationNotificationEvent");
            record.setEventPayload("{\"userId\":" + event.getUserId()
                    + ",\"email\":\"" + escape(event.getEmail())
                    + "\",\"templateCode\":\"" + escape(event.getNotificationRequest().getTemplateCode())
                    + "\",\"bizId\":\"" + escape(event.getNotificationRequest().getBizId()) + "\"}");
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception persistenceException) {
            log.error("Failed to persist user registration notification failure: {}",
                    persistenceException.getMessage(), persistenceException);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
