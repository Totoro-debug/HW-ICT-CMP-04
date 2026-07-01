package com.ecommerce.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Configuration properties for order behavior.
 */
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    private int expireMinutes = 60;
    private int maxItems = 30;
    private BigDecimal packagingFee = new BigDecimal("2.00");
    private BigDecimal freeShippingThreshold = new BigDecimal("199.00");

    public int getExpireMinutes() {
        return expireMinutes;
    }

    public void setExpireMinutes(int expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public BigDecimal getPackagingFee() {
        return packagingFee;
    }

    public void setPackagingFee(BigDecimal packagingFee) {
        this.packagingFee = packagingFee;
    }

    public BigDecimal getFreeShippingThreshold() {
        return freeShippingThreshold;
    }

    public void setFreeShippingThreshold(BigDecimal freeShippingThreshold) {
        this.freeShippingThreshold = freeShippingThreshold;
    }
}
