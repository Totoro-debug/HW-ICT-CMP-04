package com.ecommerce.logistics.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.logistics.service.LogisticsCommandService;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        OrderPaidEvent event = new OrderPaidEvent(this, 100L, 200L, "PAY001", new BigDecimal("108.00"));

        listener.onOrderPaid(event);

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    @Test
    void onPaymentSucceeded_delegatesToLogisticsCommandService() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(this, "PAY001", 100L, 200L,
                new BigDecimal("108.00"));

        listener.onPaymentSucceeded(event);

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    @Test
    void onPaymentSucceeded_failurePersistsRecordAndDoesNotThrow() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(this, "PAY001", 100L, 200L,
                new BigDecimal("108.00"));
        doThrow(new RuntimeException("downstream failed"))
                .when(logisticsCommandService).createShipmentForPaidOrder(100L);

        listener.onPaymentSucceeded(event);

        org.mockito.ArgumentCaptor<FailedEventRecord> captor = org.mockito.ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertEquals("LOGISTICS_CREATE_SHIPMENT_AFTER_PAYMENT", record.getEventType());
        assertEquals("100", record.getEventPayload());
        assertEquals(FailedEventStatus.PENDING, record.getStatus());
        assertEquals("downstream failed", record.getLastError());
    }

    @Test
    void replay_recreatesShipmentForOrderPayload() throws Exception {
        listener.replay("100");

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }
}
