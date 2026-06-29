package com.ecommerce.order.integration;

import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sending order-related notifications to users.
 */
@Service
public class OrderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

    private final LocalNotificationService notificationService;

    public OrderNotificationService(LocalNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyOrderCreated(Order order, String receiver) {
        send(buildStatusNotification(order, receiver, "ORDER_CREATED", "order_created",
                variables(order, "status", order.getStatus(), "expiresAt", formatDateTime(order.getExpiresAt()))));
    }

    public void notifyPaymentSuccess(Order order, String receiver) {
        send(buildNotification(order, receiver, NotificationChannel.SMS,
                "ORDER_PAYMENT", "payment_succeeded",
                variables(order, "paymentNo", order.getPaymentNo(), "paidAmount", order.getPaidAmount())));
    }

    public void notifyOrderShipped(Order order, String trackingNumber, String receiver) {
        send(buildNotification(order, receiver, NotificationChannel.SMS,
                "ORDER_SHIPPED", "order_shipped",
                variables(order, "status", OrderStatus.SHIPPED, "trackingNumber", trackingNumber)));
    }

    public void notifyOrderDelivered(Order order, String receiver) {
        send(buildStatusNotification(order, receiver, "ORDER_DELIVERED", "order_delivered",
                variables(order, "status", OrderStatus.DELIVERED)));
    }

    public void notifyOrderCancelled(Order order, String reason, String receiver) {
        send(buildStatusNotification(order, receiver, "ORDER_CANCELLED", "order_cancelled",
                variables(order, "status", OrderStatus.CANCELLED, "reason", reason != null ? reason : "User requested")));
    }

    public void notifyPaymentExpiring(Order order, String receiver, long minutesRemaining) {
        send(buildStatusNotification(order, receiver, "ORDER_PAYMENT_EXPIRING", "payment_expiring",
                variables(order, "status", order.getStatus(), "minutesRemaining", minutesRemaining,
                        "expiresAt", formatDateTime(order.getExpiresAt()))));
    }

    public void notifyStatusUpdate(Order order, OrderStatus newStatus, String receiver) {
        send(buildStatusNotification(order, receiver, "ORDER_STATUS", "order_status",
                variables(order, "status", newStatus)));
    }

    public void notifyBatch(List<Order> orders, String template, String receiver) {
        for (Order order : orders) {
            send(buildStatusNotification(order, receiver, "ORDER_BATCH", "order_batch",
                    variables(order, "status", order.getStatus(), "template", template)));
        }
    }

    private void send(NotificationRequest request) {
        try {
            notificationService.send(request);
            log.debug("Order notification sent: bizType={}, bizId={}, channel={}",
                    request.getBizType(), request.getBizId(), request.getChannel());
        } catch (Exception e) {
            log.warn("Failed to send order notification: bizType={}, bizId={}, error={}",
                    request.getBizType(), request.getBizId(), e.getMessage());
        }
    }

    private NotificationRequest buildStatusNotification(Order order, String receiver, String bizType,
                                                        String templateCode, Map<String, Object> variables) {
        return buildNotification(order, receiver, NotificationChannel.IN_APP, bizType, templateCode, variables);
    }

    private NotificationRequest buildNotification(Order order, String receiver, NotificationChannel channel,
                                                  String bizType, String templateCode, Map<String, Object> variables) {
        NotificationRequest request = new NotificationRequest();
        request.setBizType(bizType);
        request.setBizId(String.valueOf(order.getId()));
        request.setReceiver(receiver);
        request.setChannel(channel);
        request.setTemplateCode(templateCode);
        request.setVariables(variables);
        request.setIdempotencyKey(bizType + ":" + order.getId() + ":" + templateCode);
        return request;
    }

    private Map<String, Object> variables(Order order, Object... extraKeyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("orderId", order.getId());
        values.put("orderNo", order.getOrderNo());
        values.put("userId", order.getUserId());
        values.put("payableAmount", order.getPayableAmount());
        values.put("createdAt", formatDateTime(order.getCreatedAt()));
        for (int i = 0; i + 1 < extraKeyValues.length; i += 2) {
            if (extraKeyValues[i] != null && extraKeyValues[i + 1] != null) {
                values.put(String.valueOf(extraKeyValues[i]), extraKeyValues[i + 1]);
            }
        }
        return values;
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
