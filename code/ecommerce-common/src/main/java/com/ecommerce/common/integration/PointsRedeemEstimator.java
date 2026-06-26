package com.ecommerce.common.integration;

import java.math.BigDecimal;

/**
 * Local integration port for estimating loyalty points redemption.
 */
public interface PointsRedeemEstimator {

    int estimateRedeemPoints(BigDecimal orderAmount, Long userId);
}
