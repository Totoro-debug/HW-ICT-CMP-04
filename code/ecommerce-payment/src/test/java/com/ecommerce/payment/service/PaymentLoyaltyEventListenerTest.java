package com.ecommerce.payment.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.integration.LoyaltyCommandService;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLoyaltyEventListenerTest {

    @Mock
    private LoyaltyCommandService loyaltyCommandService;

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    private PaymentLoyaltyEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentLoyaltyEventListener(loyaltyCommandService, failedEventRecordRepository);
    }

    @Test
    @DisplayName("listener failure is persisted as failed event record")
    void testOnPaymentSucceeded_failurePersistsFailedEventRecord() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                this, "PAY001", 1L, 100L, new BigDecimal("10.00"));
        when(loyaltyCommandService.earnPaymentPoints(100L, new BigDecimal("10.00"), 1.0d))
                .thenThrow(new IllegalStateException("loyalty unavailable"));

        listener.onPaymentSucceeded(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertEquals("PaymentLoyaltyEventListener:PaymentSucceededEvent", record.getEventType());
        assertTrue(record.getEventPayload().contains("PAY001"));
        assertEquals("loyalty unavailable", record.getErrorMessage());
        assertEquals(FailedEventStatus.PENDING, record.getStatus());
    }

    @Test
    @DisplayName("listener success does not persist failure")
    void testOnPaymentSucceeded_successDoesNotPersistFailure() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                this, "PAY002", 2L, 200L, new BigDecimal("20.00"));
        when(loyaltyCommandService.earnPaymentPoints(200L, new BigDecimal("20.00"), 1.0d))
                .thenReturn(20);

        listener.onPaymentSucceeded(event);

        verify(failedEventRecordRepository, org.mockito.Mockito.never()).save(any());
    }
}
