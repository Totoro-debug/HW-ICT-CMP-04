package com.ecommerce.common.test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeConfigRegistry {
    private static final Map<String, Object> defaults = Map.ofEntries(
            Map.entry("order.expire-minutes", 60),
            Map.entry("order.max-items", 30),
            Map.entry("payment.retry-times", 5),
            Map.entry("payment.refund-fee-rate", "0.02"),
            Map.entry("invoice.tax-rate", "0.06"),
            Map.entry("cart.ttl-days", 7),
            Map.entry("loyalty.max-redeem-points-per-order", 10000),
            Map.entry("loyalty.max-redeem-ratio", "0.5"),
            Map.entry("loyalty.activity-multiplier", "1.0"),
            Map.entry("member.discount-rate", "0.95")
    );
    private static final Map<String, Object> overrides = new ConcurrentHashMap<>();
    private RuntimeConfigRegistry() {}
    public static void put(String key, Object value) { overrides.put(key, value); }
    public static Object get(String key) { return overrides.get(key); }
    public static Object getOrDefault(String key) { return overrides.getOrDefault(key, defaults.get(key)); }
    public static void remove(String key) { overrides.remove(key); }
    public static void clear() { overrides.clear(); }
    public static Map<String, Object> getAll() { return Map.copyOf(overrides); }

    public static BigDecimal getBigDecimal(String key, BigDecimal fallback) {
        Object value = getOrDefault(key);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static double getDouble(String key, double fallback) {
        Object value = getOrDefault(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int getInt(String key, int fallback) {
        Object value = getOrDefault(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String getString(String key, String fallback) {
        Object value = getOrDefault(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
