package com.ecommerce.inventory.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Cache for inventory stock summaries.
 *
 * <p>Design key semantics: inventory:summary:{skuId}; entries expire after 30 seconds.
 */
public final class InventorySummaryCache {

    private static final Cache<String, StockSnapshot> CACHE = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    private InventorySummaryCache() {
    }

    public static StockSnapshot get(Long skuId, Function<Long, StockSnapshot> loader) {
        return CACHE.get(key(skuId), ignored -> loader.apply(skuId));
    }

    public static void evict(Long skuId) {
        if (skuId != null) {
            CACHE.invalidate(key(skuId));
        }
    }

    public static void clear() {
        CACHE.invalidateAll();
    }

    private static String key(Long skuId) {
        return "inventory:summary:" + skuId;
    }

    public record StockSnapshot(int onHandStock, int reservedStock, int availableStock) {
    }
}
