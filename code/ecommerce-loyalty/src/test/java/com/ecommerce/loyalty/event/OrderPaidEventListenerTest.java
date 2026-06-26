package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderPaidEventListener}.
 */
@ExtendWith(MockitoExtension.class)
class OrderPaidEventListenerTest {

    @Mock
    private LoyaltyPointService loyaltyPointService;

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    private OrderPaidEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaidEventListener(loyaltyPointService, failedEventRecordRepository);
    }

    /**
     * Verifies that when an order is paid, the listener calculates points
     * via {@link LoyaltyPointService#calcOrderPoints} and awards them via
     * {@link LoyaltyPointService#earnPoints}.
     */
    @Test
    void testEarnPointsOnOrderPaid() {
        Long orderId = 100L;
        Long userId = 200L;
        BigDecimal paidAmount = new BigDecimal("150.00");

        OrderPaidEvent event = new OrderPaidEvent(new Object(), orderId, userId, "PAY-100", paidAmount);

        // Mock calcOrderPoints with the default activity multiplier.
        when(loyaltyPointService.calcOrderPoints(paidAmount, userId, 1.0))
                .thenReturn(16500);

        listener.onOrderPaid(event);

        // Verify calcOrderPoints was called with the correct arguments
        verify(loyaltyPointService).calcOrderPoints(
                eq(paidAmount), eq(userId), eq(1.0));

        // Verify earnPoints was called with the calculated points
        verify(loyaltyPointService).earnPoints(
                eq(userId), eq(16500), eq("ORDER"),
                eq(orderId.toString()),
                eq("Order payment reward, orderId=" + orderId));
    }

    /**
     * Verifies that when calcOrderPoints returns 0, earnPoints is NOT called.
     */
    @Test
    void testZeroPoints_doesNotEarnPoints() {
        OrderPaidEvent event = new OrderPaidEvent(new Object(), 300L, 400L, "PAY-300", BigDecimal.ZERO);

        when(loyaltyPointService.calcOrderPoints(BigDecimal.ZERO, 400L, 1.0))
                .thenReturn(0);

        listener.onOrderPaid(event);

        // Verify earnPoints was NOT called (points == 0, so the if block is skipped)
        verify(loyaltyPointService, never()).earnPoints(any(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void testAwardFailure_persistsFailedEventRecord() {
        OrderPaidEvent event = new OrderPaidEvent(new Object(), 500L, 600L, "PAY-500", new BigDecimal("88.00"));
        when(loyaltyPointService.calcOrderPoints(event.getPaidAmount(), event.getUserId(), 1.0)).thenReturn(8800);
        doThrow(new RuntimeException("award failed"))
                .when(loyaltyPointService)
                .earnPoints(any(), anyInt(), anyString(), anyString(), anyString());

        listener.onOrderPaid(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertEquals("OrderPaidEvent", record.getEventType());
        assertEquals("award failed", record.getErrorMessage());
        assertEquals(0, record.getRetryCount());
        assertEquals(false, record.isRetried());
        verify(loyaltyPointService).earnPoints(
                eq(600L), eq(8800), eq("ORDER"), eq("500"), contains("orderId=500"));
    }
}
