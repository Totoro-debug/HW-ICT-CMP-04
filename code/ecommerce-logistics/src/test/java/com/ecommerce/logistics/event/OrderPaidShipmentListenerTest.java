package com.ecommerce.logistics.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderPaidEventItem;
import com.ecommerce.logistics.service.LogisticsCommandService;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPaidShipmentListenerTest {

    @Mock
    private LogisticsCommandService logisticsCommandService;
    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    private OrderPaidShipmentListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaidShipmentListener(logisticsCommandService, failedEventRecordRepository);
    }

    @Test
    void onOrderPaid_delegatesToLogisticsCommandService() {
        OrderPaidEvent event = orderPaidEvent();

        listener.onOrderPaid(event);

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    @Test
    void onOrderPaid_failurePersistsRecordWithItemsAndDoesNotThrow() {
        OrderPaidEvent event = orderPaidEvent();
        doThrow(new RuntimeException("downstream failed"))
                .when(logisticsCommandService).createShipmentForPaidOrder(100L);

        listener.onOrderPaid(event);

        org.mockito.ArgumentCaptor<FailedEventRecord> captor = org.mockito.ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertEquals("LOGISTICS_CREATE_SHIPMENT_AFTER_PAYMENT", record.getEventType());
        assertTrue(record.getEventPayload().contains("\"eventType\":\"OrderPaidEvent\""));
        assertTrue(record.getEventPayload().contains("\"orderId\":100"));
        assertTrue(record.getEventPayload().contains("\"items\":"));
        assertEquals(FailedEventStatus.PENDING, record.getStatus());
        assertEquals("downstream failed", record.getLastError());
    }

    @Test
    void onPaymentSucceeded_delegatesToLogisticsCommandService() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(this, "PAY001", 100L, 200L,
                new BigDecimal("108.00"), LocalDateTime.parse("2026-07-01T10:15:30"));

        listener.onPaymentSucceeded(event);

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    @Test
    void onPaymentSucceeded_failurePersistsRecordWithAppendixDPayloadAndDoesNotThrow() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(this, "PAY001", 100L, 200L,
                new BigDecimal("108.00"), LocalDateTime.parse("2026-07-01T10:15:30"));
        doThrow(new RuntimeException("downstream failed"))
                .when(logisticsCommandService).createShipmentForPaidOrder(100L);

        listener.onPaymentSucceeded(event);

        org.mockito.ArgumentCaptor<FailedEventRecord> captor = org.mockito.ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertEquals("LOGISTICS_CREATE_SHIPMENT_AFTER_PAYMENT", record.getEventType());
        assertTrue(record.getEventPayload().contains("\"eventType\":\"PaymentSucceededEvent\""));
        assertTrue(record.getEventPayload().contains("\"paymentNo\":\"PAY001\""));
        assertTrue(record.getEventPayload().contains("\"orderId\":100"));
        assertTrue(record.getEventPayload().contains("\"paidAmount\":108.00"));
        assertTrue(record.getEventPayload().contains("\"paidAt\":\"2026-07-01T10:15:30\""));
        assertEquals(FailedEventStatus.PENDING, record.getStatus());
        assertEquals("downstream failed", record.getLastError());
    }

    @Test
    void replay_recreatesShipmentForLegacyOrderPayload() throws Exception {
        listener.replay("100");

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    @Test
    void replay_recreatesShipmentForJsonPayload() throws Exception {
        listener.replay("{\"eventType\":\"PaymentSucceededEvent\",\"orderId\":100}");

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    private OrderPaidEvent orderPaidEvent() {
        return new OrderPaidEvent(this, 100L, 200L, "PAY001", new BigDecimal("108.00"),
                List.of(new OrderPaidEventItem(300L, 400L, 2,
                        new BigDecimal("50.00"), new BigDecimal("100.00"))));
    }
}
