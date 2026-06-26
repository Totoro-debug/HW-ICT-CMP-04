package com.ecommerce.order.service;

import com.ecommerce.order.dto.CreateOrderRequest;

import java.util.List;

/**
 * Internal order split group used by order creation before persistence.
 */
public class OrderSplitGroup {

    private final List<CreateOrderRequest.OrderItemRequest> items;

    public OrderSplitGroup(List<CreateOrderRequest.OrderItemRequest> items) {
        this.items = items;
    }

    public List<CreateOrderRequest.OrderItemRequest> getItems() {
        return items;
    }
}
