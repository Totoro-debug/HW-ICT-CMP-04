package com.ecommerce.inventory.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventReplayHandler;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.event.OrderPaidEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InventoryOrderPaidEventListener implements FailedEventReplayHandler {

    static final String EVENT_TYPE = "InventoryOrderPaidEventListener:OrderPaidEvent";

    private static final Logger log = LoggerFactory.getLogger(InventoryOrderPaidEventListener.class);

    private final InventoryReservationServiceImpl reservationService;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public InventoryOrderPaidEventListener(InventoryReservationServiceImpl reservationService,
                                           FailedEventRecordRepository failedEventRecordRepository,
                                           ObjectMapper objectMapper) {
        this.reservationService = reservationService;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            reservationService.deductAfterPayment(event.getOrderId());
            log.info("Inventory deduction handled for paid order: orderId={}, eventId={}",
                    event.getOrderId(), event.getEventId());
        } catch (Exception ex) {
            log.error("Inventory order-paid listener failed: orderId={}, eventId={}, error={}",
                    event.getOrderId(), event.getEventId(), ex.getMessage(), ex);
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

    private void persistFailure(OrderPaidEvent event, Exception exception) {
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

    private String serializeEvent(OrderPaidEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getEventId());
        payload.put("orderId", event.getOrderId());
        payload.put("userId", event.getUserId());
        payload.put("paymentNo", event.getPaymentNo());
        payload.put("paidAmount", event.getPaidAmount());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"orderId\":" + event.getOrderId() + "}";
        }
    }
}
