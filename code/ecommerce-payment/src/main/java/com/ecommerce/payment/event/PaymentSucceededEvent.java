package com.ecommerce.payment.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentSucceededEvent extends AbstractDomainEvent {

    private final String paymentNo;
    private final Long orderId;
    private final Long userId;
    private final BigDecimal paidAmount;
    private final LocalDateTime paidAt;

    public PaymentSucceededEvent(Object source, String paymentNo, Long orderId,
                                 Long userId, BigDecimal paidAmount) {
        this(source, paymentNo, orderId, userId, paidAmount, LocalDateTime.now());
    }

    public PaymentSucceededEvent(Object source, String paymentNo, Long orderId,
                                 Long userId, BigDecimal paidAmount, LocalDateTime paidAt) {
        super(source, "PaymentSucceededEvent", orderId == null ? null : String.valueOf(orderId), null);
        this.paymentNo = paymentNo;
        this.orderId = orderId;
        this.userId = userId;
        this.paidAmount = paidAmount;
        this.paidAt = paidAt;
    }

    public String getPaymentNo() { return paymentNo; }
    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
