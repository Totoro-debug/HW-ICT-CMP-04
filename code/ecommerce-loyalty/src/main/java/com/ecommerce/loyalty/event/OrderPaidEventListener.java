package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderPaidEventItem;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Listens for {@link OrderPaidEvent} and awards loyalty points for the order.
 *
 * <p>On order paid:
 * <ol>
 *   <li>Calculate points via {@link LoyaltyPointService#calcOrderPoints}</li>
 *   <li>Award points via {@link LoyaltyPointService#earnPoints}</li>
 * </ol>
 */
@Component
public class OrderPaidEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventListener.class);

    private final LoyaltyPointService loyaltyPointService;
    private final FailedEventRecordRepository failedEventRecordRepository;

    public OrderPaidEventListener(LoyaltyPointService loyaltyPointService,
                                  FailedEventRecordRepository failedEventRecordRepository) {
        this.loyaltyPointService = loyaltyPointService;
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Received OrderPaidEvent: orderId={}, userId={}, amount={}, items={}",
                event.getOrderId(), event.getUserId(), event.getPaidAmount(), event.getItems().size());

        try {
            int points = loyaltyPointService.calcOrderPoints(
                    event.getPaidAmount(), event.getUserId(), BigDecimal.ONE);
            if (points > 0) {
                loyaltyPointService.earnPoints(
                        event.getUserId(), points, "PAYMENT",
                        event.getEventId(),
                        "Order payment reward, orderId=" + event.getOrderId());
            }
            log.info("Awarded {} points for orderId={}", points, event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to award points for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    private void persistFailure(OrderPaidEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("OrderPaidEvent");
            record.setEventPayload(buildPayload(event));
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception persistException) {
            log.error("Failed to persist loyalty compensation record for orderId={}: {}",
                    event.getOrderId(), persistException.getMessage(), persistException);
        }
    }

    private String buildPayload(OrderPaidEvent event) {
        return "{\"eventId\":\"" + event.getEventId()
                + "\",\"eventType\":\"" + event.getEventType()
                + "\",\"occurredAt\":\"" + event.getOccurredAt()
                + "\",\"aggregateId\":\"" + event.getAggregateId()
                + "\",\"traceId\":\"" + event.getTraceId()
                + "\",\"orderId\":" + event.getOrderId()
                + ",\"userId\":" + event.getUserId()
                + ",\"paidAmount\":" + event.getPaidAmount()
                + ",\"items\":[" + itemsPayload(event.getItems()) + "]}";
    }

    private String itemsPayload(List<OrderPaidEventItem> items) {
        return items.stream()
                .map(item -> "{\"skuId\":" + item.getSkuId()
                        + ",\"productId\":" + item.getProductId()
                        + ",\"quantity\":" + item.getQuantity()
                        + ",\"unitPrice\":" + item.getUnitPrice()
                        + ",\"payableAmount\":" + item.getPayableAmount() + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
