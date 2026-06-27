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
 * Loyalty-side listener entry for shipment delivered events.
 */
@Component
public class ShipmentDeliveredEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDeliveredEventListener.class);

    private final FailedEventRecordRepository failedEventRecordRepository;

    public ShipmentDeliveredEventListener(FailedEventRecordRepository failedEventRecordRepository) {
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentDelivered(AbstractDomainEvent event) {
        if (!"ShipmentDeliveredEvent".equals(event.getClass().getSimpleName())) {
            return;
        }
        try {
            handleShipmentDelivered(event);
        } catch (Exception e) {
            log.error("Failed to handle ShipmentDeliveredEvent in loyalty: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    protected void handleShipmentDelivered(AbstractDomainEvent event) {
        log.info("Received ShipmentDeliveredEvent in loyalty: shipmentId={}, orderId={}, userId={}, deliveredAt={}",
                invokeGetter(event, "getShipmentId"),
                invokeGetter(event, "getOrderId"),
                invokeGetter(event, "getUserId"),
                invokeGetter(event, "getDeliveredAt"));
        // Current loyalty rules do not award extra delivery points; this listener is the required event entry.
    }

    private void persistFailure(AbstractDomainEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("ShipmentDeliveredEvent");
            record.setEventPayload("{\"eventId\":\"" + event.getEventId()
                    + "\",\"shipmentId\":" + invokeGetter(event, "getShipmentId")
                    + ",\"orderId\":" + invokeGetter(event, "getOrderId")
                    + ",\"userId\":" + invokeGetter(event, "getUserId")
                    + ",\"deliveredAt\":\"" + invokeGetter(event, "getDeliveredAt") + "\"}");
            record.setErrorMessage(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            failedEventRecordRepository.save(record);
        } catch (Exception persistException) {
            log.error("Failed to persist ShipmentDeliveredEvent failure: eventId={}, error={}",
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
