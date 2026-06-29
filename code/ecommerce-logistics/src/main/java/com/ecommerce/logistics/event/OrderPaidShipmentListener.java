package com.ecommerce.logistics.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventReplayHandler;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.logistics.service.LogisticsCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Creates logistics shipments asynchronously after payment succeeds.
 */
@Component
public class OrderPaidShipmentListener implements FailedEventReplayHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidShipmentListener.class);
    private static final String EVENT_TYPE = "LOGISTICS_CREATE_SHIPMENT_AFTER_PAYMENT";

    private final LogisticsCommandService logisticsCommandService;
    private final FailedEventRecordRepository failedEventRecordRepository;

    public OrderPaidShipmentListener(LogisticsCommandService logisticsCommandService,
                                     FailedEventRecordRepository failedEventRecordRepository) {
        this.logisticsCommandService = logisticsCommandService;
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        createShipmentForPaidOrder(event.getOrderId(), "OrderPaidEvent");
    }

    private void createShipmentForPaidOrder(Long orderId, String eventType) {
        try {
            logisticsCommandService.createShipmentForPaidOrder(orderId);
            log.info("Shipment creation command handled for orderId={} from {}", orderId, eventType);
        } catch (Exception e) {
            log.error("Failed to create shipment for paid orderId={} from {}: {}",
                    orderId, eventType, e.getMessage(), e);
            persistFailure(orderId, eventType, e);
        }
    }

    private void persistFailure(Long orderId, String sourceEventType, Exception exception) {
        FailedEventRecord record = new FailedEventRecord();
        record.setEventType(EVENT_TYPE);
        record.setEventPayload(String.valueOf(orderId));
        record.setErrorMessage("source=" + sourceEventType + ", orderId=" + orderId + ", error=" + exception.getMessage());
        record.setLastError(exception.getMessage());
        record.setOccurredAt(LocalDateTime.now());
        record.setRetryCount(0);
        record.setRetried(false);
        record.setStatus(FailedEventStatus.PENDING);
        failedEventRecordRepository.save(record);
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void replay(String eventPayload) {
        logisticsCommandService.createShipmentForPaidOrder(Long.valueOf(eventPayload));
    }
}
