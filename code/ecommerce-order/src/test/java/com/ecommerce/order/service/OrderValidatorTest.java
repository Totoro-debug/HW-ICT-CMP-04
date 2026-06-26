package com.ecommerce.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OrderValidator}.
 */
@DisplayName("OrderValidator")
class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    @Test
    @DisplayName("validateAmount with zero amount throws ORDER_INVALID_AMOUNT")
    void testValidateAmount_zero_throwsOrderInvalidAmount() {
        assertThatThrownBy(() -> validator.validateAmount(BigDecimal.ZERO))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("Order amount must be positive")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("ORDER_INVALID_AMOUNT");
    }

    @Test
    @DisplayName("validateAmount with negative amount throws ORDER_INVALID_AMOUNT")
    void testValidateAmount_negative_throwsOrderInvalidAmount() {
        assertThatThrownBy(() -> validator.validateAmount(new BigDecimal("-50.00")))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("Order amount must be positive")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("ORDER_INVALID_AMOUNT");
    }

    @Test
    @DisplayName("validateAmount with null amount throws ORDER_INVALID_AMOUNT")
    void testValidateAmount_null_throwsOrderInvalidAmount() {
        assertThatThrownBy(() -> validator.validateAmount(null))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("Order amount must be positive")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("ORDER_INVALID_AMOUNT");
    }

    @Test
    @DisplayName("validateAmount with positive amount passes without exception")
    void testValidateAmount_positive_noException() {
        assertThatCode(() -> validator.validateAmount(new BigDecimal("100.00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateAmount(new BigDecimal("0.01")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateQuantity with zero throws VALIDATION_FAILED")
    void testValidateQuantity_zero_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateQuantity(0))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("quantity must be positive")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("validateQuantity with negative throws VALIDATION_FAILED")
    void testValidateQuantity_negative_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateQuantity(-1))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("quantity must be positive")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("validateQuantity with positive value passes")
    void testValidateQuantity_positive_noException() {
        assertThatCode(() -> validator.validateQuantity(1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateQuantity(10)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateItemsCount with zero throws VALIDATION_FAILED")
    void testValidateItemsCount_zero_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateItemsCount(0))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("at least one item")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("validateItemsCount with negative throws VALIDATION_FAILED")
    void testValidateItemsCount_negative_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateItemsCount(-5))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("at least one item")
                .extracting(ex -> ((com.ecommerce.common.exception.BusinessException) ex).getCode())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("validateItemsCount with positive value passes")
    void testValidateItemsCount_positive_noException() {
        assertThatCode(() -> validator.validateItemsCount(1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateItemsCount(5)).doesNotThrowAnyException();
    }
}
