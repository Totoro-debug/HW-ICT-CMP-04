package com.ecommerce.order.integration;

import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OrderNotificationService")
class OrderNotificationServiceTest {

    @Test
    @DisplayName("uses SMS for payment success notification with complete request fields")
    void testNotifyPaymentSuccess_usesSmsAndCompleteFields() {
        LocalNotificationService notificationService = mock(LocalNotificationService.class);
        OrderNotificationService service = new OrderNotificationService(notificationService);
        Order order = order();

        service.notifyPaymentSuccess(order, "13800138000");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.getBizType()).isEqualTo("ORDER_PAYMENT");
        assertThat(request.getBizId()).isEqualTo("100");
        assertThat(request.getReceiver()).isEqualTo("13800138000");
        assertThat(request.getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(request.getTemplateCode()).isEqualTo("payment_succeeded");
        assertThat(request.getIdempotencyKey()).isEqualTo("ORDER_PAYMENT:100:payment_succeeded");
        assertThat(request.getVariables())
                .containsEntry("orderId", 100L)
                .containsEntry("orderNo", "SO202606290001")
                .containsEntry("userId", 200L)
                .containsEntry("paymentNo", "PAY-001")
                .containsEntry("paidAmount", new BigDecimal("88.00"));
    }

    @Test
    @DisplayName("uses SMS for shipped notification")
    void testNotifyOrderShipped_usesSms() {
        LocalNotificationService notificationService = mock(LocalNotificationService.class);
        OrderNotificationService service = new OrderNotificationService(notificationService);
        Order order = order();

        service.notifyOrderShipped(order, "SF123456", "13800138000");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("order_shipped");
    }

    @Test
    @DisplayName("uses IN_APP for status update notification")
    void testNotifyStatusUpdate_usesInApp() {
        LocalNotificationService notificationService = mock(LocalNotificationService.class);
        OrderNotificationService service = new OrderNotificationService(notificationService);
        Order order = order();

        service.notifyStatusUpdate(order, OrderStatus.CANCELLED, "user:200");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("order_status");
        assertThat(captor.getValue().getVariables()).containsEntry("status", OrderStatus.CANCELLED);
    }

    private Order order() {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("SO202606290001");
        order.setUserId(200L);
        order.setStatus(OrderStatus.CREATED);
        order.setPayableAmount(new BigDecimal("99.00"));
        order.setPaidAmount(new BigDecimal("88.00"));
        order.setPaymentNo("PAY-001");
        order.setCreatedAt(LocalDateTime.of(2026, 6, 29, 10, 0, 0));
        order.setExpiresAt(LocalDateTime.of(2026, 6, 29, 11, 0, 0));
        return order;
    }
}
