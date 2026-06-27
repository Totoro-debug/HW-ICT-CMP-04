package com.ecommerce.loyalty.entity;

import java.math.BigDecimal;

/**
 * Represents the membership tier of a loyalty account.
 * Each level has an associated point-earning multiplier.
 */
public enum MemberLevel {

    NORMAL("1.0"),
    SILVER("1.1"),
    GOLD("1.1"),
    PLATINUM("1.5");

    private final BigDecimal multiplier;

    MemberLevel(String multiplier) {
        this.multiplier = new BigDecimal(multiplier);
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }
}
