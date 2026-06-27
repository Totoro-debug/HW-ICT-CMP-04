package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.money.MoneyValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates order-related data before processing.
 */
@Component
public class OrderValidator {

    private static final Logger log = LoggerFactory.getLogger(OrderValidator.class);

    /**
     * Validate that an order amount meets the payable minimum.
     *
     * @param amount the amount to validate
     */
    public void validateAmount(BigDecimal amount) {
        MoneyValidationUtil.validatePayableAmount(amount);
        log.debug("Amount validated: {}", amount);
    }

    /**
     * Validate that a quantity is positive.
     */
    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("VALIDATION_FAILED",
                    "Order item quantity must be positive, got: " + quantity);
        }
    }

    /**
     * Validate that an order has at least one item.
     */
    public void validateItemsCount(int count) {
        if (count <= 0) {
            throw new BusinessException("VALIDATION_FAILED",
                    "Order must contain at least one item");
        }
    }
}
