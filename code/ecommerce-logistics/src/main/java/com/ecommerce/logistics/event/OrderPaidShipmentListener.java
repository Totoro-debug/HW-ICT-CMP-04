package com.ecommerce.logistics.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventReplayHandler;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderPaidEventItem;
import com.ecommerce.logistics.service.LogisticsCommandService;
import com.ecommerce.payment.event.PaymentSucceededEvent;
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
        log.info("Received OrderPaidEvent for shipment creation: orderId={}, itemCount={}",
                event.getOrderId(), event.getItems().size());
        createShipmentForPaidOrder(event.getOrderId(), event.getEventType(), orderPaidPayload(event));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent for shipment creation: paymentNo={}, orderId={}, paidAmount={}, paidAt={}",
                event.getPaymentNo(), event.getOrderId(), event.getPaidAmount(), event.getPaidAt());
        createShipmentForPaidOrder(event.getOrderId(), event.getEventType(), paymentSucceededPayload(event));
    }

    private void createShipmentForPaidOrder(Long orderId, String eventType, String eventPayload) {
        try {
            logisticsCommandService.createShipmentForPaidOrder(orderId);
            log.info("Shipment creation command handled for orderId={} from {}", orderId, eventType);
        } catch (Exception e) {
            log.error("Failed to create shipment for paid orderId={} from {}: {}",
                    orderId, eventType, e.getMessage(), e);
            persistFailure(orderId, eventType, eventPayload, e);
        }
    }

    private void persistFailure(Long orderId, String sourceEventType, String eventPayload, Exception exception) {
        FailedEventRecord record = new FailedEventRecord();
        record.setEventType(EVENT_TYPE);
        record.setEventPayload(eventPayload);
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
        logisticsCommandService.createShipmentForPaidOrder(extractOrderId(eventPayload));
    }

    private String orderPaidPayload(OrderPaidEvent event) {
        return "{"
                + jsonField("eventId", event.getEventId()) + ","
                + jsonField("eventType", event.getEventType()) + ","
                + jsonField("occurredAt", event.getOccurredAt()) + ","
                + jsonField("aggregateId", event.getAggregateId()) + ","
                + jsonField("traceId", event.getTraceId()) + ","
                + jsonField("orderId", event.getOrderId()) + ","
                + jsonField("userId", event.getUserId()) + ","
                + jsonField("paidAmount", event.getPaidAmount()) + ","
                + "\"items\":" + itemsPayload(event)
                + "}";
    }

    private String paymentSucceededPayload(PaymentSucceededEvent event) {
        return "{"
                + jsonField("eventId", event.getEventId()) + ","
                + jsonField("eventType", event.getEventType()) + ","
                + jsonField("occurredAt", event.getOccurredAt()) + ","
                + jsonField("aggregateId", event.getAggregateId()) + ","
                + jsonField("traceId", event.getTraceId()) + ","
                + jsonField("paymentNo", event.getPaymentNo()) + ","
                + jsonField("orderId", event.getOrderId()) + ","
                + jsonField("paidAmount", event.getPaidAmount()) + ","
                + jsonField("paidAt", event.getPaidAt())
                + "}";
    }

    private String itemsPayload(OrderPaidEvent event) {
        StringBuilder payload = new StringBuilder("[");
        for (int i = 0; i < event.getItems().size(); i++) {
            OrderPaidEventItem item = event.getItems().get(i);
            if (i > 0) {
                payload.append(",");
            }
            payload.append("{")
                    .append(jsonField("skuId", item.getSkuId())).append(",")
                    .append(jsonField("productId", item.getProductId())).append(",")
                    .append(jsonField("quantity", item.getQuantity())).append(",")
                    .append(jsonField("unitPrice", item.getUnitPrice())).append(",")
                    .append(jsonField("payableAmount", item.getPayableAmount()))
                    .append("}");
        }
        return payload.append("]").toString();
    }

    private String jsonField(String name, Object value) {
        if (value == null) {
            return "\"" + name + "\":null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return "\"" + name + "\":" + value;
        }
        return "\"" + name + "\":\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Long extractOrderId(String eventPayload) {
        String payload = eventPayload == null ? "" : eventPayload.trim();
        if (!payload.startsWith("{")) {
            return Long.valueOf(payload);
        }

        int orderIdIndex = payload.indexOf("\"orderId\"");
        if (orderIdIndex < 0) {
            throw new IllegalArgumentException("Missing orderId in event payload");
        }
        int colonIndex = payload.indexOf(':', orderIdIndex);
        int valueStart = colonIndex + 1;
        while (valueStart < payload.length() && Character.isWhitespace(payload.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < payload.length() && Character.isDigit(payload.charAt(valueEnd))) {
            valueEnd++;
        }
        return Long.valueOf(payload.substring(valueStart, valueEnd));
    }
}
