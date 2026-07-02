package com.ecommerce.order.listener;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderCancelledEvent;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderPaymentEventHandler;
import com.ecommerce.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Internal event listeners for order domain events.
 * Handles post-commit actions that should happen after the transaction
 * is successfully committed.
 *
 * <p>Note: These are IN-MODULE listeners that handle order-internal concerns
 * (like logging, metrics, internal state updates). Cross-module effects
 * (notification, logistics, loyalty) are handled by their respective modules'
 * event listeners, which subscribe to the same events.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderRepository orderRepository;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final OrderPaymentEventHandler orderPaymentEventHandler;
    private final OrderService orderService;

    public OrderEventListener(OrderRepository orderRepository,
                              FailedEventRecordRepository failedEventRecordRepository,
                              OrderPaymentEventHandler orderPaymentEventHandler,
                              OrderService orderService) {
        this.orderRepository = orderRepository;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.orderPaymentEventHandler = orderPaymentEventHandler;
        this.orderService = orderService;
    }

    /**
     * Handle order creation event — fires AFTER the creating transaction commits.
     * Performs post-creation logging and metrics that should not roll back
     * even if they fail.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        handleNonStrongEvent(event, "OrderEventListener.onOrderCreated", this::processOrderCreated);
    }

    /**
     * Handle order paid event.
     * Updates internal order payment tracking and fires cross-module notifications.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        handleNonStrongEvent(event, "OrderEventListener.onOrderPaid", this::processOrderPaid);
    }

    /**
     * Handle order cancellation event.
     * Logs cancellation and triggers any internal cleanup.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        handleNonStrongEvent(event, "OrderEventListener.onOrderCancelled", this::processOrderCancelled);
    }

    @EventListener(condition = "#event.getClass().getName() == 'com.ecommerce.payment.event.PaymentSucceededEvent'")
    public void onPaymentSucceeded(Object event) {
        try {
            Long orderId = readLong(event, "getOrderId");
            String paymentNo = readString(event, "getPaymentNo");
            java.math.BigDecimal paidAmount = (java.math.BigDecimal) readValue(event, "getPaidAmount");
            LocalDateTime paidAt = (LocalDateTime) readValue(event, "getPaidAt");
            orderPaymentEventHandler.handlePaymentSuccess(orderId, paymentNo, paidAmount, paidAt);
        } catch (Exception ex) {
            log.error("Order PaymentSucceededEvent listener failed: {}", ex.getMessage(), ex);
            persistFailure((Object) event, "OrderEventListener.onPaymentSucceeded", ex);
        }
    }

    @EventListener(condition = "#event.getClass().getName() == 'com.ecommerce.logistics.event.ShipmentDeliveredEvent'")
    public void onShipmentDelivered(Object event) {
        try {
            Long orderId = readLong(event, "getOrderId");
            Long shipmentId = readLong(event, "getShipmentId");
            LocalDateTime deliveredAt = (LocalDateTime) readValue(event, "getDeliveredAt");
            processShipmentDelivered(orderId, shipmentId, deliveredAt);
        } catch (Exception ex) {
            log.error("Order ShipmentDeliveredEvent listener failed: {}", ex.getMessage(), ex);
            persistFailure((Object) event, "OrderEventListener.onShipmentDelivered", ex);
        }
    }

    /**
     * Async fallback listener for general order events.
     * Uses the synchronous event bus as a fallback for cases where
     * TransactionalEventListener might not fire (e.g., no active transaction).
     */
    @Async
    @EventListener
    public void onOrderCreatedFallback(OrderCreatedEvent event) {
        handleNonStrongEvent(event, "OrderEventListener.onOrderCreatedFallback",
                e -> log.debug("[OrderEventListener-Fallback] OrderCreatedEvent caught via EventListener: orderId={}",
                        e.getOrderId()));
    }

    /**
     * Async fallback for paid events.
     */
    @Async
    @EventListener
    public void onOrderPaidFallback(OrderPaidEvent event) {
        handleNonStrongEvent(event, "OrderEventListener.onOrderPaidFallback",
                e -> log.debug("[OrderEventListener-Fallback] OrderPaidEvent caught via EventListener: orderId={}",
                        e.getOrderId()));
    }

    /**
     * Async fallback for cancelled events.
     */
    @Async
    @EventListener
    public void onOrderCancelledFallback(OrderCancelledEvent event) {
        handleNonStrongEvent(event, "OrderEventListener.onOrderCancelledFallback",
                e -> log.debug("[OrderEventListener-Fallback] OrderCancelledEvent caught via EventListener: orderId={}",
                        e.getOrderId()));
    }

    private void processOrderCreated(OrderCreatedEvent event) {
        log.info("[OrderEventListener] Order created: orderId={}, userId={}, amount={}, eventId={}",
                event.getOrderId(), event.getUserId(), event.getPayableAmount(), event.getEventId());
        log.debug("OrderCreatedEvent processing complete for orderId={}", event.getOrderId());
    }

    private void processOrderPaid(OrderPaidEvent event) {
        log.info("[OrderEventListener] Order paid: orderId={}, userId={}, paymentNo={}, amount={}, eventId={}",
                event.getOrderId(), event.getUserId(), event.getPaymentNo(),
                event.getPaidAmount(), event.getEventId());

        Optional<Order> orderOpt = orderRepository.findById(event.getOrderId());
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            if (order.getStatus() == OrderStatus.PAID && order.getPaidAt() == null) {
                order.setPaidAt(LocalDateTime.now());
                orderRepository.save(order);
                log.debug("Paid timestamp updated for orderId={}", event.getOrderId());
            }
        }
    }

    private void processOrderCancelled(OrderCancelledEvent event) {
        log.info("[OrderEventListener] Order cancelled: orderId={}, userId={}, eventId={}",
                event.getOrderId(), event.getUserId(), event.getEventId());
        log.debug("OrderCancelledEvent processing complete for orderId={}", event.getOrderId());
    }

    private void processShipmentDelivered(Long orderId, Long shipmentId, LocalDateTime deliveredAt) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order not found: " + orderId));
        OrderStatus fromStatus = order.getStatus();
        if (fromStatus != OrderStatus.DELIVERED && fromStatus != OrderStatus.COMPLETED) {
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);
        }
        orderService.recordEvent(orderId, fromStatus, OrderStatus.DELIVERED,
                "SHIPMENT_DELIVERED", "LOGISTICS_SYSTEM",
                "Shipment delivered: shipmentId=" + shipmentId + ", deliveredAt=" + deliveredAt);
    }

    private Long readLong(Object event, String methodName) throws ReflectiveOperationException {
        Object value = readValue(event, methodName);
        return value instanceof Number number ? number.longValue() : null;
    }

    private String readString(Object event, String methodName) throws ReflectiveOperationException {
        Object value = readValue(event, methodName);
        return value != null ? String.valueOf(value) : null;
    }

    private Object readValue(Object event, String methodName) throws ReflectiveOperationException {
        return event.getClass().getMethod(methodName).invoke(event);
    }

    private <T extends com.ecommerce.common.event.AbstractDomainEvent> void handleNonStrongEvent(
            T event, String handlerName, Consumer<T> handler) {
        try {
            handler.accept(event);
        } catch (Exception ex) {
            log.error("Order event listener failed: handler={}, eventType={}, eventId={}, error={}",
                    handlerName, event.getClass().getSimpleName(), event.getEventId(), ex.getMessage(), ex);
            persistFailure(event, handlerName, ex);
        }
    }

    private void persistFailure(com.ecommerce.common.event.AbstractDomainEvent event,
                                String handlerName,
                                Exception exception) {
        persistFailure((Object) event, handlerName, exception);
    }

    private void persistFailure(Object event, String handlerName, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(handlerName + ":" + event.getClass().getSimpleName());
            record.setEventPayload(serializeEvent(event));
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception persistenceException) {
            log.error("Failed to persist order event failure record: {}", persistenceException.getMessage(), persistenceException);
        }
    }

    private String serializeEvent(Object event) {
        StringBuilder payload = new StringBuilder("{");
        appendJson(payload, "eventId", safeRead(event, "getEventId"));
        appendJson(payload, "eventType", safeRead(event, "getEventType"));
        appendJson(payload, "occurredAt", safeRead(event, "getOccurredAt"));
        appendJson(payload, "aggregateId", safeRead(event, "getAggregateId"));
        appendJson(payload, "traceId", safeRead(event, "getTraceId"));
        appendJson(payload, "paymentNo", safeRead(event, "getPaymentNo"));
        appendJson(payload, "orderId", safeRead(event, "getOrderId"));
        appendJson(payload, "paidAmount", safeRead(event, "getPaidAmount"));
        appendJson(payload, "paidAt", safeRead(event, "getPaidAt"));
        appendJson(payload, "shipmentId", safeRead(event, "getShipmentId"));
        appendJson(payload, "deliveredAt", safeRead(event, "getDeliveredAt"));
        payload.append("}");
        return payload.toString();
    }

    private Object safeRead(Object event, String methodName) {
        try {
            return readValue(event, methodName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void appendJson(StringBuilder payload, String field, Object value) {
        if (value == null) {
            return;
        }
        if (payload.length() > 1) {
            payload.append(',');
        }
        payload.append('"').append(field).append("\":");
        if (value instanceof Number) {
            payload.append(value);
        } else {
            payload.append('"').append(String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
    }
}
