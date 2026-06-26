package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.AbstractDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

/**
 * Loyalty-side listener entry for shipment delivered events.
 */
@Component
public class ShipmentDeliveredEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDeliveredEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentDelivered(AbstractDomainEvent event) {
        if (!"ShipmentDeliveredEvent".equals(event.getClass().getSimpleName())) {
            return;
        }
        try {
            log.info("Received ShipmentDeliveredEvent in loyalty: shipmentId={}, orderId={}, userId={}, deliveredAt={}",
                    invokeGetter(event, "getShipmentId"),
                    invokeGetter(event, "getOrderId"),
                    invokeGetter(event, "getUserId"),
                    invokeGetter(event, "getDeliveredAt"));
            // Current loyalty rules do not award extra delivery points; this listener is the required event entry.
        } catch (Exception e) {
            log.error("Failed to handle ShipmentDeliveredEvent in loyalty: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
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
