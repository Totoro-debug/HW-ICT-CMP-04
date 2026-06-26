package com.ecommerce.common.integration;

import java.math.BigDecimal;

/**
 * Read-side interface for loyalty queries exposed to other modules.
 */
public interface LoyaltyQueryService {

    int estimateRedeemPoints(BigDecimal orderAmount, Long userId);
}
