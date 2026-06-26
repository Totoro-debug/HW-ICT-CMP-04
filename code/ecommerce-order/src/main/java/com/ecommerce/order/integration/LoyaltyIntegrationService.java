package com.ecommerce.order.integration;

import com.ecommerce.common.integration.LoyaltyQueryService;
import com.ecommerce.common.money.MonetaryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Centralizes loyalty points estimation logic used by the order module.
 */
@Service
public class LoyaltyIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyIntegrationService.class);

    private static final BigDecimal POINTS_TO_YUAN_RATE = new BigDecimal("0.01");
    private static final BigDecimal MAX_REDEEM_RATIO = new BigDecimal("0.5");

    private final LoyaltyQueryService loyaltyQueryService;

    public LoyaltyIntegrationService(LoyaltyQueryService loyaltyQueryService) {
        this.loyaltyQueryService = loyaltyQueryService;
    }

    /**
     * Calculate the amount that would be deducted if the user redeems the given number of points.
     * Applies the loyalty module's estimation cap and the 50% of order amount cap.
     */
    public PointsCalculationResult calculatePointsDeduction(Long userId, BigDecimal orderAmount,
                                                            int requestedPoints) {
        if (userId == null || orderAmount == null
                || orderAmount.compareTo(BigDecimal.ZERO) <= 0
                || requestedPoints <= 0) {
            return PointsCalculationResult.zero();
        }

        try {
            int maxRedeemable = loyaltyQueryService.estimateRedeemPoints(orderAmount, userId);
            int actualPoints = Math.min(requestedPoints, maxRedeemable);

            if (actualPoints <= 0) {
                return PointsCalculationResult.zero();
            }

            BigDecimal deductionAmount = MonetaryUtil.multiply(
                    BigDecimal.valueOf(actualPoints), POINTS_TO_YUAN_RATE);

            BigDecimal maxDeduction = MonetaryUtil.multiply(orderAmount, MAX_REDEEM_RATIO);
            if (deductionAmount.compareTo(maxDeduction) > 0) {
                deductionAmount = maxDeduction;
                actualPoints = deductionAmount.divide(POINTS_TO_YUAN_RATE, 0, BigDecimal.ROUND_DOWN)
                        .intValue();
            }

            log.info("Points deduction: userId={}, requested={}, actual={}, amount={}",
                    userId, requestedPoints, actualPoints, deductionAmount);
            return new PointsCalculationResult(actualPoints, deductionAmount);
        } catch (Exception e) {
            log.warn("Failed to calculate points deduction, returning zero: {}", e.getMessage());
            return PointsCalculationResult.zero();
        }
    }

    public static class PointsCalculationResult {
        private final int points;
        private final BigDecimal amount;

        public PointsCalculationResult(int points, BigDecimal amount) {
            this.points = points;
            this.amount = amount;
        }

        public static PointsCalculationResult zero() {
            return new PointsCalculationResult(0, BigDecimal.ZERO);
        }

        public int getPoints() { return points; }
        public BigDecimal getAmount() { return amount; }
    }
}
