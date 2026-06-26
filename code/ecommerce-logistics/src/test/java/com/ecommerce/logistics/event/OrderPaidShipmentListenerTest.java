package com.ecommerce.logistics.event;

import com.ecommerce.logistics.service.LogisticsCommandService;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPaidShipmentListenerTest {

    @Mock
    private LogisticsCommandService logisticsCommandService;

    private OrderPaidShipmentListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPaidShipmentListener(logisticsCommandService);
    }

    @Test
    void onOrderPaid_delegatesToLogisticsCommandService() {
        OrderPaidEvent event = new OrderPaidEvent(this, 100L, 200L, "PAY001", new BigDecimal("108.00"));

        listener.onOrderPaid(event);

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }

    @Test
    void onPaymentSucceeded_delegatesToLogisticsCommandService() {
        PaymentSucceededEvent event = new PaymentSucceededEvent(this, "PAY001", 100L, 200L,
                new BigDecimal("108.00"));

        listener.onPaymentSucceeded(event);

        verify(logisticsCommandService).createShipmentForPaidOrder(100L);
    }
}
