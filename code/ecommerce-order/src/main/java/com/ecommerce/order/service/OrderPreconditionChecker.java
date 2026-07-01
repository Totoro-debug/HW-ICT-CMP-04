package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.order.config.OrderProperties;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates preconditions before creating an order.
 */
@Component
public class OrderPreconditionChecker {

    private static final Logger log = LoggerFactory.getLogger(OrderPreconditionChecker.class);

    private final UserQueryService userQueryService;
    private final OrderProperties orderProperties;

    public OrderPreconditionChecker(UserQueryService userQueryService,
                                    OrderProperties orderProperties) {
        this.userQueryService = userQueryService;
        this.orderProperties = orderProperties != null ? orderProperties : new OrderProperties();
    }

    /**
     * Check that all preconditions for order creation are met.
     *
     * @param userId    the user ID
     * @param itemCount the number of items in the order
     * @throws BusinessException if user does not exist
     */
    public void check(Long userId, int itemCount) {
        UserDto user = userQueryService.getUserById(userId);
        if (user == null) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "User not found: " + userId);
        }

        String status = user.getStatus();
        if ("FROZEN".equalsIgnoreCase(status)) {
            throw new BusinessException("USER_FROZEN", "User is frozen: " + userId);
        }
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            throw new BusinessException("USER_NOT_ACTIVE", "User is not active: " + userId);
        }

        if (itemCount <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "Order must have at least one item");
        }

        int maxItems = getRuntimeInt("order.max-items", orderProperties.getMaxItems());
        if (itemCount > maxItems) {
            throw new BusinessException("VALIDATION_FAILED",
                    "Order can contain at most " + maxItems + " distinct items. Got: " + itemCount);
        }

        log.debug("Order preconditions passed for userId={}, itemCount={}", userId, itemCount);
    }

    private int getRuntimeInt(String key, int fallback) {
        Object value = RuntimeConfigRegistry.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
