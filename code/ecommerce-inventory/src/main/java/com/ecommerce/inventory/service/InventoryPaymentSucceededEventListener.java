package com.ecommerce.inventory.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventReplayHandler;
import com.ecommerce.common.event.FailedEventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InventoryPaymentSucceededEventListener implements FailedEventReplayHandler {

    static final String EVENT_TYPE = "InventoryPaymentSucceededEventListener:PaymentSucceededEvent";

    private static final Logger log = LoggerFactory.getLogger(InventoryPaymentSucceededEventListener.class);

    private final InventoryReservationServiceImpl reservationService;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public InventoryPaymentSucceededEventListener(InventoryReservationServiceImpl reservationService,
                                                  FailedEventRecordRepository failedEventRecordRepository,
                                                  ObjectMapper objectMapper) {
        this.reservationService = reservationService;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(condition = "#event.getClass().getName() == 'com.ecommerce.payment.event.PaymentSucceededEvent'")
    public void onPaymentSucceeded(Object event) {
        Long orderId = getLong(event, "getOrderId");
        try {
            reservationService.deductAfterPayment(orderId);
            log.info("Inventory deduction handled for payment succeeded: paymentNo={}, orderId={}, paidAmount={}, paidAt={}, eventId={}",
                    getString(event, "getPaymentNo"), orderId, getValue(event, "getPaidAmount"),
                    getValue(event, "getPaidAt"), getString(event, "getEventId"));
        } catch (Exception ex) {
            log.error("Inventory payment-succeeded listener failed: paymentNo={}, orderId={}, eventId={}, error={}",
                    getString(event, "getPaymentNo"), orderId, getString(event, "getEventId"), ex.getMessage(), ex);
            persistFailure(event, ex);
        }
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void replay(String eventPayload) throws Exception {
        JsonNode root = objectMapper.readTree(eventPayload);
        JsonNode orderIdNode = root.get("orderId");
        if (orderIdNode == null || orderIdNode.isNull()) {
            throw new IllegalArgumentException("Missing orderId in inventory failed event payload");
        }
        reservationService.deductAfterPayment(orderIdNode.asLong());
    }

    private void persistFailure(Object event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(EVENT_TYPE);
            record.setEventPayload(serializeEvent(event));
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception persistenceException) {
            log.error("Failed to persist inventory event failure record: {}",
                    persistenceException.getMessage(), persistenceException);
        }
    }

    private String serializeEvent(Object event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", getValue(event, "getEventId"));
        payload.put("eventType", getValue(event, "getEventType"));
        payload.put("occurredAt", stringifyValue(getValue(event, "getOccurredAt")));
        payload.put("aggregateId", getValue(event, "getAggregateId"));
        payload.put("traceId", getValue(event, "getTraceId"));
        payload.put("paymentNo", getValue(event, "getPaymentNo"));
        payload.put("orderId", getValue(event, "getOrderId"));
        payload.put("paidAmount", getValue(event, "getPaidAmount"));
        payload.put("paidAt", stringifyValue(getValue(event, "getPaidAt")));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"orderId\":" + getValue(event, "getOrderId") + "}";
        }
    }

    private Object stringifyValue(Object value) {
        if (value instanceof LocalDateTime) {
            return String.valueOf(value);
        }
        return value;
    }

    private Long getLong(Object event, String methodName) {
        Object value = getValue(event, methodName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private String getString(Object event, String methodName) {
        Object value = getValue(event, methodName);
        return value == null ? null : String.valueOf(value);
    }

    private Object getValue(Object event, String methodName) {
        try {
            Method method = event.getClass().getMethod(methodName);
            return method.invoke(event);
        } catch (Exception ex) {
            return null;
        }
    }
}
