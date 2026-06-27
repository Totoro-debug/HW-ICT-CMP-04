package com.ecommerce.order.listener;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrderEventListener")
class OrderEventListenerTest {

    @Test
    @DisplayName("non-strong listener failure is persisted and swallowed")
    void testOnOrderPaid_failurePersistsRecordAndDoesNotThrow() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        FailedEventRecordRepository failedEventRecordRepository = mock(FailedEventRecordRepository.class);
        when(orderRepository.findById(100L)).thenThrow(new IllegalStateException("database unavailable"));
        when(failedEventRecordRepository.save(any(FailedEventRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderEventListener listener = new OrderEventListener(orderRepository, failedEventRecordRepository);
        OrderPaidEvent event = new OrderPaidEvent(this, 100L, 200L, "PAY-001", new BigDecimal("10.00"));

        assertThatCode(() -> listener.onOrderPaid(event)).doesNotThrowAnyException();

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertThat(record.getEventType()).isEqualTo("OrderEventListener.onOrderPaid:OrderPaidEvent");
        assertThat(record.getErrorMessage()).isEqualTo("database unavailable");
        assertThat(record.getStatus()).isEqualTo(FailedEventStatus.PENDING);
    }
}
