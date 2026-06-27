package com.ecommerce.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for monetary calculations.
 * All monetary operations must use this class rather than raw BigDecimal arithmetic.
 *
 * <p>Intermediate add/subtract/multiply operations keep full BigDecimal precision.
 * Call roundToCent() explicitly at persistence or external response boundaries.
 */
public final class MonetaryUtil {

    private static final int SCALE = 2;

    private MonetaryUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Rounds a BigDecimal to 2 decimal places (cents) using HALF_UP.
     */
    public static BigDecimal roundToCent(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Adds two BigDecimal values without rounding the intermediate result.
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        BigDecimal augend = a != null ? a : BigDecimal.ZERO;
        BigDecimal addend = b != null ? b : BigDecimal.ZERO;
        return augend.add(addend);
    }

    /**
     * Subtracts the second BigDecimal from the first without rounding the intermediate result.
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        BigDecimal minuend = a != null ? a : BigDecimal.ZERO;
        BigDecimal subtrahend = b != null ? b : BigDecimal.ZERO;
        return minuend.subtract(subtrahend);
    }

    /**
     * Multiplies two BigDecimal values without rounding the intermediate result.
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        BigDecimal multiplicand = a != null ? a : BigDecimal.ZERO;
        BigDecimal multiplier = b != null ? b : BigDecimal.ZERO;
        return multiplicand.multiply(multiplier);
    }

    public static BigDecimal addAndRoundToCent(BigDecimal a, BigDecimal b) {
        return roundToCent(add(a, b));
    }

    public static BigDecimal subtractAndRoundToCent(BigDecimal a, BigDecimal b) {
        return roundToCent(subtract(a, b));
    }

    public static BigDecimal multiplyAndRoundToCent(BigDecimal a, BigDecimal b) {
        return roundToCent(multiply(a, b));
    }
}
