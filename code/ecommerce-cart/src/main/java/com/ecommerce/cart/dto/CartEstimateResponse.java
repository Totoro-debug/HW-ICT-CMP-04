package com.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for cart price estimation.
 */
public class CartEstimateResponse {

    public static class ApplicableCoupon {
        private Long couponId;
        private String couponCode;
        private String name;
        private BigDecimal discountAmount;

        public Long getCouponId() {
            return couponId;
        }

        public void setCouponId(Long couponId) {
            this.couponId = couponId;
        }

        public String getCouponCode() {
            return couponCode;
        }

        public void setCouponCode(String couponCode) {
            this.couponCode = couponCode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getDiscountAmount() {
            return discountAmount;
        }

        public void setDiscountAmount(BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
        }
    }

    private BigDecimal itemTotal;
    private BigDecimal shippingFee;
    private BigDecimal packagingFee;
    private BigDecimal discountAmount;
    private BigDecimal pointsDeductionAmount;
    private BigDecimal payableAmount;
    private BigDecimal fullReductionDiscount;
    private BigDecimal memberDiscount;
    private List<ApplicableCoupon> applicableCoupons;

    public CartEstimateResponse() {
    }

    public BigDecimal getItemTotal() {
        return itemTotal;
    }

    public void setItemTotal(BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getPackagingFee() {
        return packagingFee;
    }

    public void setPackagingFee(BigDecimal packagingFee) {
        this.packagingFee = packagingFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getPointsDeductionAmount() {
        return pointsDeductionAmount;
    }

    public void setPointsDeductionAmount(BigDecimal pointsDeductionAmount) {
        this.pointsDeductionAmount = pointsDeductionAmount;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }

    public void setPayableAmount(BigDecimal payableAmount) {
        this.payableAmount = payableAmount;
    }

    public BigDecimal getFullReductionDiscount() {
        return fullReductionDiscount;
    }

    public void setFullReductionDiscount(BigDecimal fullReductionDiscount) {
        this.fullReductionDiscount = fullReductionDiscount;
    }

    public BigDecimal getMemberDiscount() {
        return memberDiscount;
    }

    public void setMemberDiscount(BigDecimal memberDiscount) {
        this.memberDiscount = memberDiscount;
    }

    public List<ApplicableCoupon> getApplicableCoupons() {
        return applicableCoupons;
    }

    public void setApplicableCoupons(List<ApplicableCoupon> applicableCoupons) {
        this.applicableCoupons = applicableCoupons;
    }
}
