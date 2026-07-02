package com.ecommerce.inventory.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryPaymentSucceededEventListener")
@ExtendWith(MockitoExtension.class)
class InventoryPaymentSucceededEventListenerTest {

    @Mock
    private InventoryReservationServiceImpl reservationService;

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    @Test
    @DisplayName("onPaymentSucceeded persists failed event and does not rethrow")
    void testOnPaymentSucceeded_failurePersistsRecordAndDoesNotThrow() {
        InventoryPaymentSucceededEventListener listener = new InventoryPaymentSucceededEventListener(
                reservationService, failedEventRecordRepository, new ObjectMapper());
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "event-1", "PaymentSucceededEvent", LocalDateTime.of(2026, 7, 1, 12, 29),
                "10", "trace-1", "PAY-1", 10L, new BigDecimal("30.00"),
                LocalDateTime.of(2026, 7, 1, 12, 30));
        doThrow(new RuntimeException("deduct failed")).when(reservationService).deductAfterPayment(10L);
        when(failedEventRecordRepository.save(any(FailedEventRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        listener.onPaymentSucceeded(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertThat(record.getEventType()).isEqualTo(InventoryPaymentSucceededEventListener.EVENT_TYPE);
        assertThat(record.getEventPayload()).contains(
                "\"eventId\":\"event-1\"",
                "\"eventType\":\"PaymentSucceededEvent\"",
                "\"aggregateId\":\"10\"",
                "\"traceId\":\"trace-1\"",
                "\"paymentNo\":\"PAY-1\"",
                "\"orderId\":10",
                "\"paidAmount\":30.00",
                "\"paidAt\":");
        assertThat(record.getErrorMessage()).isEqualTo("deduct failed");
        assertThat(record.getLastError()).isEqualTo("deduct failed");
        assertThat(record.getStatus()).isEqualTo(FailedEventStatus.PENDING);
    }

    @Test
    @DisplayName("replay deducts inventory from failed event payload")
    void testReplay_deductsInventoryFromPayload() throws Exception {
        InventoryPaymentSucceededEventListener listener = new InventoryPaymentSucceededEventListener(
                reservationService, failedEventRecordRepository, new ObjectMapper());

        listener.replay("{\"eventType\":\"PaymentSucceededEvent\",\"orderId\":10}");

        verify(reservationService).deductAfterPayment(10L);
    }

    static class PaymentSucceededEvent {
        private final String eventId;
        private final String eventType;
        private final LocalDateTime occurredAt;
        private final String aggregateId;
        private final String traceId;
        private final String paymentNo;
        private final Long orderId;
        private final BigDecimal paidAmount;
        private final LocalDateTime paidAt;

        PaymentSucceededEvent(String eventId, String eventType, LocalDateTime occurredAt,
                              String aggregateId, String traceId, String paymentNo,
                              Long orderId, BigDecimal paidAmount, LocalDateTime paidAt) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.occurredAt = occurredAt;
            this.aggregateId = aggregateId;
            this.traceId = traceId;
            this.paymentNo = paymentNo;
            this.orderId = orderId;
            this.paidAmount = paidAmount;
            this.paidAt = paidAt;
        }

        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public LocalDateTime getOccurredAt() { return occurredAt; }
        public String getAggregateId() { return aggregateId; }
        public String getTraceId() { return traceId; }
        public String getPaymentNo() { return paymentNo; }
        public Long getOrderId() { return orderId; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public LocalDateTime getPaidAt() { return paidAt; }
    }
}
