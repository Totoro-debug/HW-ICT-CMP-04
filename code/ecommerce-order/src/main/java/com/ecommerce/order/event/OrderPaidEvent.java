package com.ecommerce.order.event;

import com.ecommerce.common.event.OrderPaidEventItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * Backward-compatible order package alias for the common OrderPaidEvent contract.
 */
public class OrderPaidEvent extends com.ecommerce.common.event.OrderPaidEvent {

    public OrderPaidEvent(Object source, Long orderId, Long userId,
                          String paymentNo, BigDecimal paidAmount) {
        super(source, orderId, userId, paymentNo, paidAmount);
    }

    public OrderPaidEvent(Object source, Long orderId, Long userId,
                          String paymentNo, BigDecimal paidAmount,
                          List<OrderPaidEventItem> items) {
        super(source, orderId, userId, paymentNo, paidAmount, items);
    }
}
