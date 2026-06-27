package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.AbstractDomainEvent;
import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentDeliveredEventListenerTest {

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    @Test
    void testShipmentDelivered_failurePersistsFailedEventRecord() {
        ShipmentDeliveredEventListener listener = new ShipmentDeliveredEventListener(failedEventRecordRepository) {
            @Override
            protected void handleShipmentDelivered(AbstractDomainEvent event) {
                throw new RuntimeException("shipment listener failed");
            }
        };
        ShipmentDeliveredEvent event = new ShipmentDeliveredEvent(new Object(), 1L, 10L, 20L,
                LocalDateTime.of(2026, 6, 28, 1, 2));

        listener.onShipmentDelivered(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        assertEquals("ShipmentDeliveredEvent", captor.getValue().getEventType());
        assertEquals("shipment listener failed", captor.getValue().getErrorMessage());
        assertEquals(0, captor.getValue().getRetryCount());
    }

    static class ShipmentDeliveredEvent extends AbstractDomainEvent {
        private final Long shipmentId;
        private final Long orderId;
        private final Long userId;
        private final LocalDateTime deliveredAt;

        ShipmentDeliveredEvent(Object source, Long shipmentId, Long orderId, Long userId, LocalDateTime deliveredAt) {
            super(source);
            this.shipmentId = shipmentId;
            this.orderId = orderId;
            this.userId = userId;
            this.deliveredAt = deliveredAt;
        }

        public Long getShipmentId() { return shipmentId; }
        public Long getOrderId() { return orderId; }
        public Long getUserId() { return userId; }
        public LocalDateTime getDeliveredAt() { return deliveredAt; }
    }
}
