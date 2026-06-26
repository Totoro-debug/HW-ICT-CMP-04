package com.ecommerce.promotion.service;

import java.util.List;

/**
 * Promotion-provided command interface for order creation to record coupon usage.
 */
public interface PromotionUsageCommandService {

    /**
     * Mark selected user coupons as used by an order.
     */
    void markCouponsUsed(Long userId, Long orderId, List<Long> userCouponIds);
}
