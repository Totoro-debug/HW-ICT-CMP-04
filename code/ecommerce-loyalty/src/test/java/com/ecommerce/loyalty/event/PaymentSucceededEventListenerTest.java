package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.AbstractDomainEvent;
import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededEventListenerTest {

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    @Test
    void testPaymentSucceeded_failurePersistsFailedEventRecord() {
        PaymentSucceededEventListener listener = new PaymentSucceededEventListener(failedEventRecordRepository) {
            @Override
            protected void handlePaymentSucceeded(AbstractDomainEvent event) {
                throw new RuntimeException("payment listener failed");
            }
        };
        PaymentSucceededEvent event = new PaymentSucceededEvent(new Object(), "PAY-1", 10L, 20L, new BigDecimal("12.34"));

        listener.onPaymentSucceeded(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        assertEquals("PaymentSucceededEvent", captor.getValue().getEventType());
        assertEquals("payment listener failed", captor.getValue().getErrorMessage());
        assertEquals(0, captor.getValue().getRetryCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("eventType"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("aggregateId"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("traceId"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("paidAt"));
        org.junit.jupiter.api.Assertions.assertFalse(captor.getValue().getEventPayload().contains("userId"));
    }

    static class PaymentSucceededEvent extends AbstractDomainEvent {
        private final String paymentNo;
        private final Long orderId;
        private final Long userId;
        private final BigDecimal paidAmount;
        private final LocalDateTime paidAt;

        PaymentSucceededEvent(Object source, String paymentNo, Long orderId, Long userId, BigDecimal paidAmount) {
            super(source);
            this.paymentNo = paymentNo;
            this.orderId = orderId;
            this.userId = userId;
            this.paidAmount = paidAmount;
            this.paidAt = LocalDateTime.of(2026, 6, 28, 1, 2);
        }

        public String getPaymentNo() { return paymentNo; }
        public Long getOrderId() { return orderId; }
        public Long getUserId() { return userId; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public LocalDateTime getPaidAt() { return paidAt; }
    }
}
