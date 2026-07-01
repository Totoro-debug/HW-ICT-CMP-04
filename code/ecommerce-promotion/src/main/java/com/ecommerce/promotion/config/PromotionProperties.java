package com.ecommerce.promotion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for promotion behavior.
 */
@ConfigurationProperties(prefix = "promotion")
public class PromotionProperties {

    private static final List<String> DEFAULT_STACK_ORDER = List.of(
            "FULL_REDUCTION", "COUPON", "MEMBER_DISCOUNT");

    private List<String> stackOrder = new ArrayList<>(DEFAULT_STACK_ORDER);

    public List<String> getStackOrder() {
        return stackOrder;
    }

    public void setStackOrder(List<String> stackOrder) {
        this.stackOrder = stackOrder == null || stackOrder.isEmpty()
                ? new ArrayList<>(DEFAULT_STACK_ORDER)
                : new ArrayList<>(stackOrder);
    }
}
