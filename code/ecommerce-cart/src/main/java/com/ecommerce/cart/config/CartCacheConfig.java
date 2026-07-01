package com.ecommerce.cart.config;

import com.ecommerce.cart.cache.CartData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for the Caffeine Cache used to store shopping carts.
 *
 * <p>Creates a {@link Cache} bean with 7-day TTL, keyed by cart:{userId}.
 */
@Configuration
@EnableConfigurationProperties(CartProperties.class)
public class CartCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CartCacheConfig.class);

    /**
     * Maximum number of cart entries in the cache.
     */
    private static final long MAX_CART_ENTRIES = 10_000;

    private final CartProperties cartProperties;

    public CartCacheConfig(CartProperties cartProperties) {
        this.cartProperties = cartProperties;
    }

    /**
     * Creates a Caffeine Cache bean for storing {@link CartData} keyed by cart:{userId}.
     * TTL is read from {@code cart.ttl-days}.
     */
    @Bean
    public Cache<String, CartData> cartCache() {
        Duration cartTtl = Duration.ofDays(cartProperties.getTtlDays());
        log.info("Initializing cart cache with TTL={}, maxSize={}", cartTtl, MAX_CART_ENTRIES);
        return Caffeine.newBuilder()
                .expireAfterWrite(cartTtl)
                .maximumSize(MAX_CART_ENTRIES)
                .recordStats()
                .build();
    }
}
