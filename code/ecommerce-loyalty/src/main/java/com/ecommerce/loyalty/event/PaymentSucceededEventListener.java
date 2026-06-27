package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.AbstractDomainEvent;
import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * Loyalty-side listener entry for payment success events.
 */
@Component
public class PaymentSucceededEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededEventListener.class);

    private final FailedEventRecordRepository failedEventRecordRepository;

    public PaymentSucceededEventListener(FailedEventRecordRepository failedEventRecordRepository) {
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(AbstractDomainEvent event) {
        if (!"PaymentSucceededEvent".equals(event.getClass().getSimpleName())) {
            return;
        }
        try {
            handlePaymentSucceeded(event);
        } catch (Exception e) {
            log.error("Failed to handle PaymentSucceededEvent in loyalty: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    protected void handlePaymentSucceeded(AbstractDomainEvent event) {
        log.info("Received PaymentSucceededEvent in loyalty: paymentNo={}, orderId={}, userId={}, paidAmount={}",
                invokeGetter(event, "getPaymentNo"),
                invokeGetter(event, "getOrderId"),
                invokeGetter(event, "getUserId"),
                invokeGetter(event, "getPaidAmount"));
        // Payment points are awarded by payment's post-payment adapter through LoyaltyCommandService.
    }

    private void persistFailure(AbstractDomainEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("PaymentSucceededEvent");
            record.setEventPayload("{\"eventId\":\"" + event.getEventId()
                    + "\",\"paymentNo\":\"" + invokeGetter(event, "getPaymentNo")
                    + "\",\"orderId\":" + invokeGetter(event, "getOrderId")
                    + ",\"userId\":" + invokeGetter(event, "getUserId")
                    + ",\"paidAmount\":" + invokeGetter(event, "getPaidAmount") + "}");
            record.setErrorMessage(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            failedEventRecordRepository.save(record);
        } catch (Exception persistException) {
            log.error("Failed to persist PaymentSucceededEvent failure: eventId={}, error={}",
                    event.getEventId(), persistException.getMessage(), persistException);
        }
    }

    private Object invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }
}
