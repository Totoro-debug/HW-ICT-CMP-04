package com.ecommerce.common.integration;

import java.math.BigDecimal;

/**
 * Write-side interface for loyalty commands exposed to other modules.
 */
public interface LoyaltyCommandService {

    int earnPaymentPoints(Long userId, BigDecimal orderAmount, double activityMultiplier);

    int redeemPoints(Long userId, int points, BigDecimal orderAmount);

    void freezePoints(Long userId, int points, String bizType, String bizId, String description);

    void unfreezePoints(Long userId, int points, String bizType, String bizId, String description);

    void consumeFrozenPoints(Long userId, int points, String bizType, String bizId, String description);

    void expirePoints();
}
