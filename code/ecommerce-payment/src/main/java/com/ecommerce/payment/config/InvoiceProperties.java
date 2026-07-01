package com.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Invoice module configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "invoice")
public class InvoiceProperties {

    private BigDecimal taxRate = new BigDecimal("0.06");
    private int maxTitleLength = 100;

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public int getMaxTitleLength() {
        return maxTitleLength;
    }

    public void setMaxTitleLength(int maxTitleLength) {
        this.maxTitleLength = maxTitleLength;
    }
}
