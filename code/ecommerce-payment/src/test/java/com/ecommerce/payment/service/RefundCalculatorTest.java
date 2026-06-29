package com.ecommerce.payment.service;

import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.payment.config.PaymentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link RefundCalculator}.
 */
class RefundCalculatorTest {

    private RefundCalculator calculator;

    @BeforeEach
    void setUp() {
        PaymentConfig config = new PaymentConfig();
        calculator = new RefundCalculator(config);
    }

    @Test
    @DisplayName("standard refund is rounded to cent with HALF_UP")
    void testCalculate_standardRefund_appliesFee() {
        BigDecimal result = calculator.calculate(new BigDecimal("100.005"));

        assertEquals(new BigDecimal("98.00"), result);
    }

    @Test
    @DisplayName("large amount refund only applies configured fee rate")
    void testCalculate_largeAmount_appliesOnlyFeeRate() {
        BigDecimal result = calculator.calculate(new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("980.00"), result);
    }

    @Test
    @DisplayName("zero and negative paid amount are rejected")
    void testCalculate_nonPositivePaidAmount_rejected() {
        assertThrows(OrderValidationException.class, () -> calculator.calculate(BigDecimal.ZERO));
        assertThrows(OrderValidationException.class, () -> calculator.calculate(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("refund amount must not exceed paid amount")
    void testValidateRefundAmount_exceedsPaid_rejected() {
        assertThrows(OrderValidationException.class,
                () -> calculator.validateRefundAmount(new BigDecimal("10.01"), new BigDecimal("10.00")));
    }
}
