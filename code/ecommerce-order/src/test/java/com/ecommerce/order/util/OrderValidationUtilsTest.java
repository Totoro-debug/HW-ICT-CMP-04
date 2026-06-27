package com.ecommerce.order.util;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.OrderValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderValidationUtils")
class OrderValidationUtilsTest {

    @Test
    @DisplayName("validateOrderAmount uses OrderValidationException for too-low amount")
    void testValidateOrderAmount_tooLow_throwsOrderValidationException() {
        assertThatThrownBy(() -> OrderValidationUtils.validateOrderAmount(new BigDecimal("0.009")))
                .isInstanceOf(OrderValidationException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PAYABLE_AMOUNT_TOO_LOW");
    }

    @Test
    @DisplayName("validateOrderAmount uses OrderValidationException for too-large amount")
    void testValidateOrderAmount_tooLarge_throwsOrderValidationException() {
        assertThatThrownBy(() -> OrderValidationUtils.validateOrderAmount(new BigDecimal("50000.01")))
                .isInstanceOf(OrderValidationException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ORDER_AMOUNT_TOO_LARGE");
    }

    @Test
    @DisplayName("validateOrderAmount accepts minimum payable amount")
    void testValidateOrderAmount_minimumAccepted() {
        assertThatCode(() -> OrderValidationUtils.validateOrderAmount(new BigDecimal("0.01")))
                .doesNotThrowAnyException();
    }
}
