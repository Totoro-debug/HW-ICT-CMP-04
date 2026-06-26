package com.ecommerce.logistics.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.time.LocalDateTime;

/**
 * Event published after a shipment is delivered.
 */
public class ShipmentDeliveredEvent extends AbstractDomainEvent {

    private final Long shipmentId;
    private final Long orderId;
    private final Long userId;
    private final LocalDateTime deliveredAt;

    public ShipmentDeliveredEvent(Object source, Long shipmentId, Long orderId,
                                  Long userId, LocalDateTime deliveredAt) {
        super(source);
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.userId = userId;
        this.deliveredAt = deliveredAt;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }
}
