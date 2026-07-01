package com.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Payment module configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfig {

    /**
     * Refund fee rate. 0.02 = 2%.
     * <p>The refund formula is
     * paidAmount * (1 - refundFeeRate) = paidAmount * 0.98.
     */
    private BigDecimal refundFeeRate = BigDecimal.valueOf(0.02);

    private int retryTimes = 5;
    private int callbackTimeoutSeconds = 5;

    /**
     * Shared secret/signature value used to authenticate payment gateway callbacks.
     * Tests use the default value via X-Payment-Signature.
     */
    private String callbackSignature = "valid-signature";

    public BigDecimal getRefundFeeRate() { return refundFeeRate; }
    public void setRefundFeeRate(BigDecimal refundFeeRate) { this.refundFeeRate = refundFeeRate; }

    public int getRetryTimes() { return retryTimes; }
    public void setRetryTimes(int retryTimes) { this.retryTimes = retryTimes; }

    public int getCallbackTimeoutSeconds() { return callbackTimeoutSeconds; }
    public void setCallbackTimeoutSeconds(int callbackTimeoutSeconds) { this.callbackTimeoutSeconds = callbackTimeoutSeconds; }

    public String getCallbackSignature() { return callbackSignature; }
    public void setCallbackSignature(String callbackSignature) { this.callbackSignature = callbackSignature; }
}
