package com.ecommerce.order.service;

import com.ecommerce.order.dto.CreateOrderRequest;

import java.util.List;

/**
 * Internal strategy for grouping order items into child-order creation units.
 */
public interface OrderSplitStrategy {

    List<OrderSplitGroup> split(CreateOrderRequest request);
}
