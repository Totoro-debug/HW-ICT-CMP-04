package com.ecommerce.logistics.service;

import com.ecommerce.logistics.entity.FreightTemplate;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache for freight templates using key logistics:freight:{templateId} and 30 minute TTL.
 */
@Component
public class FreightTemplateCache {

    private static final Duration TTL = Duration.ofMinutes(30);
    private final Cache<String, Optional<FreightTemplate>> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .build();

    public Optional<FreightTemplate> get(Long templateId, Supplier<Optional<FreightTemplate>> loader) {
        return cache.get(key(templateId), ignored -> loader.get());
    }

    public void evict(Long templateId) {
        if (templateId != null) {
            cache.invalidate(key(templateId));
        }
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    String key(Long templateId) {
        return "logistics:freight:" + templateId;
    }
}
