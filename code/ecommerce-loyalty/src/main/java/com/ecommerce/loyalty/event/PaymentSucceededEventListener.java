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
 * Loyalty-side listener entry for payment success events.
 */
@Component
public class PaymentSucceededEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(AbstractDomainEvent event) {
        if (!"PaymentSucceededEvent".equals(event.getClass().getSimpleName())) {
            return;
        }
        try {
            log.info("Received PaymentSucceededEvent in loyalty: paymentNo={}, orderId={}, userId={}, paidAmount={}",
                    invokeGetter(event, "getPaymentNo"),
                    invokeGetter(event, "getOrderId"),
                    invokeGetter(event, "getUserId"),
                    invokeGetter(event, "getPaidAmount"));
            // Payment points are awarded by payment's post-payment adapter through LoyaltyCommandService.
        } catch (Exception e) {
            log.error("Failed to handle PaymentSucceededEvent in loyalty: eventId={}, error={}",
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
