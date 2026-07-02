package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unified domain event published after an order payment is confirmed.
 * Listened to by logistics, loyalty, and notification modules.
 */
public class OrderPaidEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long userId;
    private final String paymentNo;
    private final BigDecimal paidAmount;
    private final List<OrderPaidEventItem> items;

    public OrderPaidEvent(Object source, Long orderId, Long userId,
                          String paymentNo, BigDecimal paidAmount) {
        this(source, orderId, userId, paymentNo, paidAmount, List.of());
    }

    public OrderPaidEvent(Object source, Long orderId, Long userId,
                          String paymentNo, BigDecimal paidAmount,
                          List<OrderPaidEventItem> items) {
        super(source, "OrderPaidEvent", orderId == null ? null : String.valueOf(orderId), null);
        this.orderId = orderId;
        this.userId = userId;
        this.paymentNo = paymentNo;
        this.paidAmount = paidAmount;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public List<OrderPaidEventItem> getItems() {
        return items;
    }
}
