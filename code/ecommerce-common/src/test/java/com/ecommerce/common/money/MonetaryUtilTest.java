package com.ecommerce.common.money;

import com.ecommerce.common.exception.OrderValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MonetaryUtil")
class MonetaryUtilTest {

    @Nested
    @DisplayName("roundToCent")
    class RoundToCent {

        @Test
        @DisplayName("rounds 0.005 up to 0.01 because HALF_UP is used")
        void testRoundToCent_roundsHalfUp() {
            BigDecimal amount = new BigDecimal("0.005");
            BigDecimal result = MonetaryUtil.roundToCent(amount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("0.01"));
        }

        @Test
        @DisplayName("preserves values that already have exactly two decimal places")
        void testRoundToCent_preservesTwoDecimals() {
            BigDecimal amount = new BigDecimal("10.25");
            BigDecimal result = MonetaryUtil.roundToCent(amount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.25"));
        }

        @Test
        @DisplayName("handles null input by returning BigDecimal.ZERO")
        void testRoundToCent_nullReturnsZero() {
            BigDecimal result = MonetaryUtil.roundToCent(null);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("rounds negative amounts with HALF_UP rounding")
        void testRoundToCent_negativeAmounts() {
            BigDecimal negativeAmount = new BigDecimal("-0.005");
            BigDecimal result = MonetaryUtil.roundToCent(negativeAmount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("-0.01"));
        }

        @Test
        @DisplayName("rounds values with three or more decimals to two decimal places")
        void testRoundToCent_truncatesExtraDecimals() {
            BigDecimal amount = new BigDecimal("3.14159");
            BigDecimal result = MonetaryUtil.roundToCent(amount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("3.14"));
        }
    }

    @Nested
    @DisplayName("arithmetic helpers")
    class ArithmeticHelpers {

        @Test
        @DisplayName("adds two amounts without rounding intermediate precision")
        void testAdd_keepsIntermediatePrecision() {
            BigDecimal a = new BigDecimal("10.005");
            BigDecimal b = new BigDecimal("5.005");
            BigDecimal result = MonetaryUtil.add(a, b);
            assertThat(result).isEqualByComparingTo(new BigDecimal("15.010"));
        }

        @Test
        @DisplayName("add handles null values as BigDecimal.ZERO")
        void testAdd_nullHandling() {
            BigDecimal result = MonetaryUtil.add(null, null);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("subtracts two amounts without rounding intermediate precision")
        void testSubtract_keepsIntermediatePrecision() {
            BigDecimal a = new BigDecimal("10.00");
            BigDecimal b = new BigDecimal("3.005");
            BigDecimal result = MonetaryUtil.subtract(a, b);
            assertThat(result).isEqualByComparingTo(new BigDecimal("6.995"));
        }

        @Test
        @DisplayName("multiplies two amounts without rounding intermediate precision")
        void testMultiply_keepsIntermediatePrecision() {
            BigDecimal a = new BigDecimal("10.00");
            BigDecimal b = new BigDecimal("0.333");
            BigDecimal result = MonetaryUtil.multiply(a, b);
            assertThat(result).isEqualByComparingTo(new BigDecimal("3.33000"));
        }

        @Test
        @DisplayName("explicit rounded helpers round only when requested")
        void testExplicitRoundedHelpers() {
            assertThat(MonetaryUtil.multiplyAndRoundToCent(new BigDecimal("10.00"), new BigDecimal("0.333")))
                    .isEqualByComparingTo(new BigDecimal("3.33"));
        }
    }

    @Nested
    @DisplayName("money validation")
    class MoneyValidation {

        @Test
        @DisplayName("discount amount must not be negative")
        void testDiscount_negativeThrowsOrderValidationException() {
            assertThatThrownBy(() -> MoneyValidationUtil.validateDiscountAmount(new BigDecimal("-0.01"), new BigDecimal("10.00")))
                    .isInstanceOf(OrderValidationException.class);
        }

        @Test
        @DisplayName("discount amount must not exceed item amount")
        void testDiscount_exceedsItemThrowsOrderValidationException() {
            assertThatThrownBy(() -> MoneyValidationUtil.validateDiscountAmount(new BigDecimal("10.01"), new BigDecimal("10.00")))
                    .isInstanceOf(OrderValidationException.class);
        }

        @Test
        @DisplayName("payable amount must be at least 0.01")
        void testPayable_tooLowThrowsOrderValidationException() {
            assertThatThrownBy(() -> MoneyValidationUtil.validatePayableAmount(BigDecimal.ZERO))
                    .isInstanceOf(OrderValidationException.class);
            MoneyValidationUtil.validatePayableAmount(new BigDecimal("0.01"));
        }
    }
}
