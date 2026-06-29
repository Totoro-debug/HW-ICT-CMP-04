package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutService")
class OrderTimeoutServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private OrderService orderService;
    @Mock
    private InventoryReservationService inventoryReservationService;

    @InjectMocks
    private OrderTimeoutService orderTimeoutService;

    private Order expiredOrder;

    @BeforeEach
    void setUp() {
        expiredOrder = new Order();
        expiredOrder.setId(1L);
        expiredOrder.setOrderNo("SO202606070001");
        expiredOrder.setUserId(100L);
        expiredOrder.setStatus(OrderStatus.CREATED);
        expiredOrder.setPayableAmount(new BigDecimal("150.00"));
        expiredOrder.setExpiresAt(LocalDateTime.now().minusHours(1));
    }

    @Test
    @DisplayName("timeout cancels expired order and releases inventory")
    void testTimeout_releasesInventory() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.CREATED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredOrder));

        orderTimeoutService.cancelExpiredOrders();

        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryReservationService).release(1L);
        verify(orderService).recordEvent(eq(1L), eq(OrderStatus.CREATED), eq(OrderStatus.CANCELLED),
                eq("TIMEOUT_CANCEL"), eq("SYSTEM"), anyString());
        verify(eventPublisher).publish(any(com.ecommerce.order.event.OrderCancelledEvent.class));
    }
}
