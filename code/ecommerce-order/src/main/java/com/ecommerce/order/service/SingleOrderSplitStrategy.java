package com.ecommerce.order.service;

import com.ecommerce.order.dto.CreateOrderRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default split strategy: keep all items in one order group to preserve the frozen API contract.
 */
@Component
public class SingleOrderSplitStrategy implements OrderSplitStrategy {

    @Override
    public List<OrderSplitGroup> split(CreateOrderRequest request) {
        return List.of(new OrderSplitGroup(request.getItems()));
    }
}
