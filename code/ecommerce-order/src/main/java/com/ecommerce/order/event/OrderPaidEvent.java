package com.ecommerce.order.event;

import java.math.BigDecimal;

/**
 * Backward-compatible order package alias for the common OrderPaidEvent contract.
 */
public class OrderPaidEvent extends com.ecommerce.common.event.OrderPaidEvent {

    public OrderPaidEvent(Object source, Long orderId, Long userId,
                          String paymentNo, BigDecimal paidAmount) {
        super(source, orderId, userId, paymentNo, paidAmount);
    }
}
