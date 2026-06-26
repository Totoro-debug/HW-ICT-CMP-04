package com.ecommerce.promotion.service;

import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;

/**
 * Promotion-provided local interface used by order and cart to calculate discounts.
 */
public interface PromotionCalculationService {

    /**
     * Calculate all applicable promotions for an order or cart estimate.
     */
    PromotionCalculateResponse calculate(PromotionCalculateRequest request);
}
