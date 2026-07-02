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
@Table(name = "refunds", indexes = {
        @Index(name = "idx_refund_no", columnList = "refund_no", unique = true),
        @Index(name = "idx_refund_request_no", columnList = "refund_request_no", unique = true),
        @Index(name = "idx_refund_payment_no", columnList = "payment_no"),
        @Index(name = "idx_refund_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_user_id", columnList = "user_id"),
        @Index(name = "idx_refund_status", columnList = "status")
})
public class RefundRecord extends BaseEntity {

    @Column(name = "refund_no", nullable = false, unique = true, length = 64)
    private String refundNo;

    @Column(name = "refund_request_no", nullable = false, unique = true, length = 64)
    private String refundRequestNo;

    @Column(name = "payment_no", nullable = false, length = 64)
    private String paymentNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    private Long reviewerId;

    private Long warehouseAcceptorId;

    @Column(length = 500)
    private String reviewNote;

    private LocalDateTime completedAt;

    private LocalDateTime settledAt;

    @Column(length = 64)
    private String settlementBatchNo;

    public RefundRecord() {
    }

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public String getRefundRequestNo() { return refundRequestNo; }
    public void setRefundRequestNo(String refundRequestNo) { this.refundRequestNo = refundRequestNo; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Long getWarehouseAcceptorId() { return warehouseAcceptorId; }
    public void setWarehouseAcceptorId(Long warehouseAcceptorId) { this.warehouseAcceptorId = warehouseAcceptorId; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public String getSettlementBatchNo() { return settlementBatchNo; }
    public void setSettlementBatchNo(String settlementBatchNo) { this.settlementBatchNo = settlementBatchNo; }
}
