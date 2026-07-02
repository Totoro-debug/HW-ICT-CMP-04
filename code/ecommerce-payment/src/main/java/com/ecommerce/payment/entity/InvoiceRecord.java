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
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoice_no", columnList = "invoice_no", unique = true),
        @Index(name = "idx_invoice_order_id", columnList = "order_id"),
        @Index(name = "idx_invoice_user_id", columnList = "user_id"),
        @Index(name = "idx_invoice_status", columnList = "status")
})
public class InvoiceRecord extends BaseEntity {

    @Column(name = "invoice_no", nullable = false, unique = true, length = 64)
    private String invoiceNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceType invoiceType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "remaining_invoiceable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingInvoiceableAmount;

    @Column(name = "title", length = 200)
    private String invoiceTitle;

    @Column(length = 50)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    private LocalDateTime issuedAt;

    public InvoiceRecord() {
    }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public InvoiceType getInvoiceType() { return invoiceType; }
    public void setInvoiceType(InvoiceType invoiceType) { this.invoiceType = invoiceType; }
    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public void setInvoiceAmount(BigDecimal invoiceAmount) { this.invoiceAmount = invoiceAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getRemainingInvoiceableAmount() { return remainingInvoiceableAmount; }
    public void setRemainingInvoiceableAmount(BigDecimal remainingInvoiceableAmount) { this.remainingInvoiceableAmount = remainingInvoiceableAmount; }
    public String getInvoiceTitle() { return invoiceTitle; }
    public void setInvoiceTitle(String invoiceTitle) { this.invoiceTitle = invoiceTitle; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
}
