package com.ecommerce.logistics.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.time.LocalDateTime;

/**
 * Event published after a shipment is delivered.
 */
public class ShipmentDeliveredEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long shipmentId;
    private final LocalDateTime deliveredAt;

    public ShipmentDeliveredEvent(Object source, Long orderId, Long shipmentId,
                                  LocalDateTime deliveredAt) {
        this(source, orderId, shipmentId, deliveredAt, null);
    }

    public ShipmentDeliveredEvent(Object source, Long orderId, Long shipmentId,
                                  LocalDateTime deliveredAt, String traceId) {
        super(source, "ShipmentDeliveredEvent", orderId == null ? null : String.valueOf(orderId), traceId);
        this.orderId = orderId;
        this.shipmentId = shipmentId;
        this.deliveredAt = deliveredAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }
}
