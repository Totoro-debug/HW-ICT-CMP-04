package com.ecommerce.loyalty.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for POST /api/v1/loyalty/points/estimate-redeem.
 */
public class PointsEstimateRequest {

    @NotNull
    @Positive
    private BigDecimal orderAmount;

    @Min(0)
    private int redeemPoints;

    public PointsEstimateRequest() {
    }

    public BigDecimal getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount) {
        this.orderAmount = orderAmount;
    }

    public int getRedeemPoints() {
        return redeemPoints;
    }

    public void setRedeemPoints(int redeemPoints) {
        this.redeemPoints = redeemPoints;
    }
}
