package com.ecommerce.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for cart behavior.
 */
@ConfigurationProperties(prefix = "cart")
public class CartProperties {

    private int ttlDays = 7;
    private int maxItems = 100;

    public int getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }
}
