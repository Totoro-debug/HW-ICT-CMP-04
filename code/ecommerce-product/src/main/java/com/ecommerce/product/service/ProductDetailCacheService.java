package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductDetailResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache for public product detail responses.
 *
 * <p>Designed key format: product:detail:{skuId}; TTL: 10 minutes.</p>
 */
@Service
public class ProductDetailCacheService {

    private final Cache<String, ProductDetailResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public Optional<ProductDetailResponse> get(Long skuId) {
        return Optional.ofNullable(cache.getIfPresent(key(skuId)));
    }

    public void put(Long skuId, ProductDetailResponse response) {
        cache.put(key(skuId), response);
    }

    public void evict(Long skuId) {
        cache.invalidate(key(skuId));
    }

    String key(Long skuId) {
        return "product:detail:" + skuId;
    }
}
