package com.ecommerce.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "loyalty")
public class LoyaltyProperties {

    private int pointsPerYuan = 1;
    private int redeemRate = 100;
    private int maxRedeemPointsPerOrder = 10_000;
    private BigDecimal maxRedeemRatio = new BigDecimal("0.5");
    private int expireMonths = 12;
    private int reviewRewardPoints = 20;

    public int getPointsPerYuan() {
        return pointsPerYuan;
    }

    public void setPointsPerYuan(int pointsPerYuan) {
        this.pointsPerYuan = pointsPerYuan;
    }

    public int getRedeemRate() {
        return redeemRate;
    }

    public void setRedeemRate(int redeemRate) {
        this.redeemRate = redeemRate;
    }

    public int getMaxRedeemPointsPerOrder() {
        return maxRedeemPointsPerOrder;
    }

    public void setMaxRedeemPointsPerOrder(int maxRedeemPointsPerOrder) {
        this.maxRedeemPointsPerOrder = maxRedeemPointsPerOrder;
    }

    public BigDecimal getMaxRedeemRatio() {
        return maxRedeemRatio;
    }

    public void setMaxRedeemRatio(BigDecimal maxRedeemRatio) {
        this.maxRedeemRatio = maxRedeemRatio;
    }

    public int getExpireMonths() {
        return expireMonths;
    }

    public void setExpireMonths(int expireMonths) {
        this.expireMonths = expireMonths;
    }

    public int getReviewRewardPoints() {
        return reviewRewardPoints;
    }

    public void setReviewRewardPoints(int reviewRewardPoints) {
        this.reviewRewardPoints = reviewRewardPoints;
    }
}
