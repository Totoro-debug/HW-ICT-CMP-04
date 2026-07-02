package com.ecommerce.promotion.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Appendix C coupon model synchronized from coupon templates and user coupons.
 */
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Column(name = "coupon_code", nullable = false, unique = true)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(name = "discount_rate", precision = 6, scale = 4)
    private BigDecimal discountRate;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "threshold_amount", precision = 18, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    public Coupon() {
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public CouponType getType() {
        return type;
    }

    public void setType(CouponType type) {
        this.type = type;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }
}
