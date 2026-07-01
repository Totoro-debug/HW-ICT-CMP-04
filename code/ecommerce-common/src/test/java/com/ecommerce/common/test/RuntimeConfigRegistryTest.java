package com.ecommerce.common.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeConfigRegistryTest {

    @AfterEach
    void tearDown() {
        RuntimeConfigRegistry.clear();
    }

    @Test
    void appendixBDefaultsAreRegistered() {
        assertEquals(60, RuntimeConfigRegistry.getOrDefault("order.expire-minutes"));
        assertEquals(30, RuntimeConfigRegistry.getOrDefault("order.max-items"));
        assertEquals(5, RuntimeConfigRegistry.getOrDefault("payment.retry-times"));
        assertEquals("0.02", RuntimeConfigRegistry.getOrDefault("payment.refund-fee-rate"));
        assertEquals("0.06", RuntimeConfigRegistry.getOrDefault("invoice.tax-rate"));
        assertEquals(7, RuntimeConfigRegistry.getOrDefault("cart.ttl-days"));
        assertEquals(10000, RuntimeConfigRegistry.getOrDefault("loyalty.max-redeem-points-per-order"));
        assertEquals("0.5", RuntimeConfigRegistry.getOrDefault("loyalty.max-redeem-ratio"));
    }

    @Test
    void overridesTakePrecedenceOverDefaults() {
        RuntimeConfigRegistry.put("order.expire-minutes", 15);
        RuntimeConfigRegistry.put("loyalty.max-redeem-ratio", "0.25");

        assertEquals(15, RuntimeConfigRegistry.getInt("order.expire-minutes", 60));
        assertEquals(new BigDecimal("0.25"), RuntimeConfigRegistry.getBigDecimal("loyalty.max-redeem-ratio", BigDecimal.ONE));
    }
}
