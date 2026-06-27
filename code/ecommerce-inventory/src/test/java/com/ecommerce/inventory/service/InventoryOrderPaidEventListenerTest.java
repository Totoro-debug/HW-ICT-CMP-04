package com.ecommerce.inventory.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.event.OrderPaidEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryOrderPaidEventListener")
@ExtendWith(MockitoExtension.class)
class InventoryOrderPaidEventListenerTest {

    @Mock
    private InventoryReservationServiceImpl reservationService;

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    @Test
    @DisplayName("onOrderPaid persists failed event and does not rethrow")
    void testOnOrderPaid_failurePersistsRecordAndDoesNotThrow() {
        InventoryOrderPaidEventListener listener = new InventoryOrderPaidEventListener(
                reservationService, failedEventRecordRepository, new ObjectMapper());
        OrderPaidEvent event = new OrderPaidEvent(this, 10L, 20L, "PAY-1", new BigDecimal("30.00"));
        doThrow(new RuntimeException("deduct failed")).when(reservationService).deductAfterPayment(10L);
        when(failedEventRecordRepository.save(any(FailedEventRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        listener.onOrderPaid(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertThat(record.getEventType()).isEqualTo(InventoryOrderPaidEventListener.EVENT_TYPE);
        assertThat(record.getEventPayload()).contains("\"orderId\":10");
        assertThat(record.getErrorMessage()).isEqualTo("deduct failed");
        assertThat(record.getLastError()).isEqualTo("deduct failed");
        assertThat(record.getStatus()).isEqualTo(FailedEventStatus.PENDING);
    }

    @Test
    @DisplayName("replay deducts inventory from failed event payload")
    void testReplay_deductsInventoryFromPayload() throws Exception {
        InventoryOrderPaidEventListener listener = new InventoryOrderPaidEventListener(
                reservationService, failedEventRecordRepository, new ObjectMapper());

        listener.replay("{\"orderId\":10}");

        verify(reservationService).deductAfterPayment(10L);
    }
}
