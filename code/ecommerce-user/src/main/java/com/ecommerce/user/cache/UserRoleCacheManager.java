package com.ecommerce.user.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Manages user role cache entries with the fixed architecture key user:roles:{userId}.
 */
@Component
public class UserRoleCacheManager {

    static final Duration ROLE_TTL = Duration.ofMinutes(30);

    private final Cache<String, List<String>> roleCache;

    public UserRoleCacheManager() {
        this.roleCache = Caffeine.newBuilder()
                .expireAfterWrite(ROLE_TTL)
                .build();
    }

    public Optional<List<String>> getRoles(Long userId) {
        List<String> roles = roleCache.getIfPresent(key(userId));
        return roles == null ? Optional.empty() : Optional.of(roles);
    }

    public void putRoles(Long userId, List<String> roles) {
        roleCache.put(key(userId), immutableCopy(roles));
    }

    public void evict(Long userId) {
        roleCache.invalidate(key(userId));
    }

    public void refresh(Long userId, List<String> roles) {
        putRoles(userId, roles);
    }

    public String key(Long userId) {
        return "user:roles:" + userId;
    }

    private List<String> immutableCopy(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(roles));
    }
}
