package com.ecommerce.common.integration;

import java.math.BigDecimal;

/**
 * Cross-module port for calculating freight at order creation time.
 */
public interface FreightCalculationService {

    BigDecimal calculateFreight(BigDecimal itemTotal, String province, BigDecimal weightKg, Integer itemCount);
}
