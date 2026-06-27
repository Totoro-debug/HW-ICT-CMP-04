package com.ecommerce.common.money;

import com.ecommerce.common.exception.OrderValidationException;

import java.math.BigDecimal;

/**
 * Utility methods for order amount domain validation.
 */
public final class MoneyValidationUtil {

    public static final BigDecimal MIN_PAYABLE_AMOUNT = new BigDecimal("0.01");
    public static final String CODE_DISCOUNT_AMOUNT_INVALID = "DISCOUNT_AMOUNT_INVALID";
    public static final String CODE_PAYABLE_AMOUNT_TOO_LOW = "PAYABLE_AMOUNT_TOO_LOW";

    private MoneyValidationUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static void validateDiscountAmount(BigDecimal discountAmount, BigDecimal itemAmount) {
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        BigDecimal item = itemAmount != null ? itemAmount : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new OrderValidationException(CODE_DISCOUNT_AMOUNT_INVALID,
                    "Discount amount must not be negative");
        }
        if (discount.compareTo(item) > 0) {
            throw new OrderValidationException(CODE_DISCOUNT_AMOUNT_INVALID,
                    "Discount amount must not exceed item amount");
        }
    }

    public static void validatePayableAmount(BigDecimal payableAmount) {
        if (payableAmount == null || payableAmount.compareTo(MIN_PAYABLE_AMOUNT) < 0) {
            throw new OrderValidationException(CODE_PAYABLE_AMOUNT_TOO_LOW,
                    "Payable amount must be at least 0.01");
        }
    }
}
