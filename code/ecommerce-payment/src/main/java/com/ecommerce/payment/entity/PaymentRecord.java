package com.ecommerce.payment.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_no", columnList = "payment_no", unique = true),
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_status", columnList = "status")
})
public class PaymentRecord extends BaseEntity {

    @Column(name = "payment_no", nullable = false, unique = true, length = 64)
    private String paymentNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 128)
    private String clientPaymentNo;

    @Column(length = 64)
    private String callbackSequence;

    @Column(columnDefinition = "TEXT")
    private String callbackData;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private LocalDateTime settledAt;

    @Column(length = 64)
    private String settlementBatchNo;

    public PaymentRecord() {
    }

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getOrderAmount() { return amount; }
    public void setOrderAmount(BigDecimal orderAmount) { this.amount = orderAmount; }
    public BigDecimal getPaidAmount() { return amount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.amount = paidAmount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getClientPaymentNo() { return clientPaymentNo; }
    public void setClientPaymentNo(String clientPaymentNo) { this.clientPaymentNo = clientPaymentNo; }
    public String getCallbackSequence() { return callbackSequence; }
    public void setCallbackSequence(String callbackSequence) { this.callbackSequence = callbackSequence; }
    public String getCallbackData() { return callbackData; }
    public void setCallbackData(String callbackData) { this.callbackData = callbackData; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public String getSettlementBatchNo() { return settlementBatchNo; }
    public void setSettlementBatchNo(String settlementBatchNo) { this.settlementBatchNo = settlementBatchNo; }
}
