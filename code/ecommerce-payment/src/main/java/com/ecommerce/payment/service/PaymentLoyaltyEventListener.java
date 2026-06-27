package com.ecommerce.payment.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.integration.LoyaltyCommandService;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Handles loyalty points as a non-critical post-payment action.
 */
@Component
public class PaymentLoyaltyEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentLoyaltyEventListener.class);

    private final LoyaltyCommandService loyaltyCommandService;
    private final FailedEventRecordRepository failedEventRecordRepository;

    public PaymentLoyaltyEventListener(LoyaltyCommandService loyaltyCommandService,
                                       FailedEventRecordRepository failedEventRecordRepository) {
        this.loyaltyCommandService = loyaltyCommandService;
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    @EventListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        try {
            if (event.getUserId() == null || event.getPaidAmount() == null) {
                log.warn("Skip payment points because userId or paidAmount is missing: paymentNo={}, orderId={}",
                        event.getPaymentNo(), event.getOrderId());
                return;
            }
            int earned = loyaltyCommandService.earnPaymentPoints(event.getUserId(), event.getPaidAmount(), 1.0d);
            log.info("Payment points earned: paymentNo={}, userId={}, points={}",
                    event.getPaymentNo(), event.getUserId(), earned);
        } catch (Exception ex) {
            log.error("Failed to earn payment points, ignored for payment flow: paymentNo={}, orderId={}, error={}",
                    event.getPaymentNo(), event.getOrderId(), ex.getMessage(), ex);
            persistFailure(event, ex);
        }
    }

    private void persistFailure(PaymentSucceededEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("PaymentLoyaltyEventListener:PaymentSucceededEvent");
            record.setEventPayload("{\"paymentNo\":\"" + event.getPaymentNo()
                    + "\",\"orderId\":" + event.getOrderId()
                    + ",\"userId\":" + event.getUserId() + "}");
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception persistException) {
            log.error("Failed to persist payment loyalty event failure for paymentNo={}: {}",
                    event.getPaymentNo(), persistException.getMessage(), persistException);
        }
    }
}
