package com.ecommerce.cart.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cart storage implementation using Caffeine Cache with 7-day TTL.
 * Key format: cart:{userId}.
 */
@Component
public class CartCacheManager {

    private static final Logger log = LoggerFactory.getLogger(CartCacheManager.class);

    private final Cache<String, CartData> cartCache;

    public CartCacheManager(Cache<String, CartData> cartCache) {
        this.cartCache = cartCache;
    }

    /**
     * Retrieves the cart for the given user from the cache.
     *
     * @param userId the user ID
     * @return the cart data, or null if not present or expired
     */
    public CartData getCart(Long userId) {
        String key = cartKey(userId);
        CartData cart = cartCache.getIfPresent(key);
        if (cart != null) {
            log.debug("Cart cache hit for key={}", key);
        } else {
            log.debug("Cart cache miss for key={}", key);
        }
        return cart;
    }

    /**
     * Stores or updates the cart for the given user in the cache.
     *
     * @param cart the cart data to store
     */
    public void saveCart(CartData cart) {
        cart.setUpdatedAt(java.time.LocalDateTime.now());
        String key = cartKey(cart.getUserId());
        cartCache.put(key, cart);
        log.debug("Cart cached for key={}", key);
    }

    /**
     * Removes the cart for the given user from the cache.
     *
     * @param userId the user ID
     */
    public void removeCart(Long userId) {
        String key = cartKey(userId);
        cartCache.invalidate(key);
        log.debug("Cart cache invalidated for key={}", key);
    }

    private String cartKey(Long userId) {
        return "cart:" + userId;
    }
}
