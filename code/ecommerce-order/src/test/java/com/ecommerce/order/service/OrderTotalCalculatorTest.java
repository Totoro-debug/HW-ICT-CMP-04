package com.ecommerce.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderTotalCalculator")
class OrderTotalCalculatorTest {

    private final OrderTotalCalculator calculator = new OrderTotalCalculator();

    @Test
    @DisplayName("calculate includes shipping fee")
    void testCalculate_includesShippingFee() {
        BigDecimal payableAmount = calculator.calculate(
                new BigDecimal("100.00"),
                new BigDecimal("8.00"),
                new BigDecimal("2.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(payableAmount).isEqualTo(new BigDecimal("110.00"));
    }

    @Test
    @DisplayName("calculate applies full formula")
    void testCalculate_fullFormula() {
        BigDecimal payableAmount = calculator.calculate(
                new BigDecimal("150.00"),
                new BigDecimal("8.00"),
                new BigDecimal("3.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"));

        assertThat(payableAmount).isEqualTo(new BigDecimal("146.00"));
    }

    @Test
    @DisplayName("calculate rounds HALF_UP and enforces minimum 0.01")
    void testCalculate_roundHalfUpAndMinimum() {
        BigDecimal rounded = calculator.calculate(
                new BigDecimal("100.005"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        assertThat(rounded).isEqualTo(new BigDecimal("100.01"));

        BigDecimal minimum = calculator.calculate(
                new BigDecimal("0.001"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        assertThat(minimum).isEqualTo(new BigDecimal("0.01"));
    }
}
