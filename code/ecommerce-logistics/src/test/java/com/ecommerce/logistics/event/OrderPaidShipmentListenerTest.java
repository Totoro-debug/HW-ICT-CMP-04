package com.ecommerce.logistics.event;

import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.repository.ShipmentRepository;
import com.ecommerce.logistics.service.ShipmentService;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaidShipmentListenerTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentService shipmentService;

    @Mock
    private OrderQueryService orderQueryService;

    private OrderPaidShipmentListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaidShipmentListener(shipmentRepository, shipmentService, orderQueryService);
    }

    @Test
    void onOrderPaid_createsShipmentFromOrderFulfillmentInfo() {
        OrderPaidEvent event = new OrderPaidEvent(this, 100L, 200L, "PAY001", new BigDecimal("108.00"));
        OrderDto order = new OrderDto();
        order.setOrderId(100L);
        order.setUserId(200L);
        order.setShippingFee(new BigDecimal("8.00"));
        order.setAddressSnapshot("{\"province\":\"Shanghai\"}");

        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(orderQueryService.getOrder(100L)).thenReturn(order);

        listener.onOrderPaid(event);

        verify(shipmentService).createShipment(100L, 200L,
                new BigDecimal("8.00"), "{\"province\":\"Shanghai\"}");
    }

    @Test
    void onOrderPaid_existingShipment_doesNotCreateDuplicate() {
        OrderPaidEvent event = new OrderPaidEvent(this, 100L, 200L, "PAY001", new BigDecimal("108.00"));
        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.of(new Shipment()));

        listener.onOrderPaid(event);

        verify(orderQueryService, never()).getOrder(100L);
        verify(shipmentService, never()).createShipment(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
