package com.ecommerce.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Logistics module configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "logistics")
public class LogisticsProperties {

    private String defaultCarrier = "LOCAL_EXPRESS";
    private BigDecimal freeShippingThreshold = new BigDecimal("199.00");
    private String callbackSecret;

    public String getDefaultCarrier() {
        return defaultCarrier;
    }

    public void setDefaultCarrier(String defaultCarrier) {
        this.defaultCarrier = defaultCarrier;
    }

    public BigDecimal getFreeShippingThreshold() {
        return freeShippingThreshold;
    }

    public void setFreeShippingThreshold(BigDecimal freeShippingThreshold) {
        this.freeShippingThreshold = freeShippingThreshold;
    }

    public String getCallbackSecret() {
        return callbackSecret;
    }

    public void setCallbackSecret(String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }
}
