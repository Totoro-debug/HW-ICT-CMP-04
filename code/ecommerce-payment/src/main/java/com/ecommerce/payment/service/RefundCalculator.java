package com.ecommerce.payment.service;

import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.payment.config.PaymentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Calculates refund amounts based on the configured fee rate.
 */
@Component
public class RefundCalculator {

    private static final Logger log = LoggerFactory.getLogger(RefundCalculator.class);

    private final PaymentConfig paymentConfig;

    public RefundCalculator(PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;
    }

    /**
     * Calculates the refund amount from the paid amount.
     */
    public BigDecimal calculate(BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("REFUND_AMOUNT_INVALID",
                    "Paid amount must be greater than 0 for refund");
        }

        BigDecimal feeRate = paymentConfig.getRefundFeeRate();
        BigDecimal refundFactor = BigDecimal.ONE.subtract(feeRate);

        BigDecimal refund = MonetaryUtil.roundToCent(MonetaryUtil.multiply(paidAmount, refundFactor));
        validateRefundAmount(refund, paidAmount);

        log.debug("Refund calculated: paid={}, factor={}, refund={}",
                paidAmount, refundFactor, refund);
        return refund;
    }

    public void validateRefundAmount(BigDecimal refundAmount, BigDecimal paidAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("REFUND_AMOUNT_INVALID",
                    "Refund amount must be greater than 0");
        }
        if (paidAmount == null || refundAmount.compareTo(paidAmount) > 0) {
            throw new OrderValidationException("REFUND_AMOUNT_EXCEEDED",
                    "Refund amount must not exceed paid amount");
        }
    }
}
