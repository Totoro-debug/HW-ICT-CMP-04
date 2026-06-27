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

    public OrderEventListener(OrderRepository orderRepository,
                              FailedEventRecordRepository failedEventRecordRepository) {
        this.orderRepository = orderRepository;
        this.failedEventRecordRepository = failedEventRecordRepository;
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
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType(handlerName + ":" + event.getClass().getSimpleName());
            record.setEventPayload("{\"eventId\":\"" + event.getEventId() + "\"}");
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
}
