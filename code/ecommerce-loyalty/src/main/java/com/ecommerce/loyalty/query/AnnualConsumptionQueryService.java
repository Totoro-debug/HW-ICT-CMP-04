package com.ecommerce.loyalty.query;

import java.math.BigDecimal;

/**
 * Port used by loyalty to query a user's annual paid consumption without
 * directly accessing order tables or repositories.
 */
public interface AnnualConsumptionQueryService {

    /**
     * Returns the annual paid consumption amount for a user.
     *
     * @param userId the user ID
     * @return paid consumption in the current calendar year
     */
    BigDecimal getAnnualConsumption(Long userId);
}
