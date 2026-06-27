package com.ecommerce.common.notification;

import com.ecommerce.common.event.AbstractDomainEvent;
import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapts core domain events to notification requests without depending on business modules.
 */
@Component
public class CoreNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(CoreNotificationEventListener.class);

    private final LocalNotificationService notificationService;
    private final FailedEventRecordRepository failedEventRecordRepository;
    private final ObjectMapper objectMapper;

    public CoreNotificationEventListener(LocalNotificationService notificationService,
                                         FailedEventRecordRepository failedEventRecordRepository,
                                         ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.failedEventRecordRepository = failedEventRecordRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onDomainEvent(AbstractDomainEvent event) {
        Optional<NotificationRequest> request = toNotificationRequest(event);
        if (request.isEmpty()) {
            return;
        }

        try {
            notificationService.send(request.get());
        } catch (Exception ex) {
            log.error("Notification listener failed: eventType={}, eventId={}, error={}",
                    event.getClass().getSimpleName(), event.getEventId(), ex.getMessage(), ex);
            persistFailure(event, ex);
        }
    }

    private Optional<NotificationRequest> toNotificationRequest(AbstractDomainEvent event) {
        String eventType = event.getClass().getSimpleName();
        return switch (eventType) {
            case "UserRegisteredEvent" -> Optional.of(buildUserRegisteredNotification(event));
            case "OrderCreatedEvent" -> Optional.of(buildOrderCreatedNotification(event));
            case "OrderPaidEvent" -> Optional.of(buildOrderPaidNotification(event));
            case "PaymentSucceededEvent" -> Optional.of(buildPaymentSucceededNotification(event));
            case "RefundCompletedEvent" -> Optional.of(buildRefundCompletedNotification(event));
            default -> Optional.empty();
        };
    }

    private NotificationRequest buildUserRegisteredNotification(AbstractDomainEvent event) {
        Object userId = read(event, "userId");
        Object email = read(event, "email");
        Object nickname = read(event, "nickname");
        return base(event, "USER_REGISTER", String.valueOf(userId),
                email != null ? String.valueOf(email) : receiverForUser(userId),
                NotificationChannel.EMAIL, "WELCOME",
                variables("userId", userId, "email", email, "nickname", nickname));
    }

    private NotificationRequest buildOrderCreatedNotification(AbstractDomainEvent event) {
        Object orderId = read(event, "orderId");
        Object userId = read(event, "userId");
        Object payableAmount = read(event, "payableAmount");
        return base(event, "ORDER_CREATED", String.valueOf(orderId), receiverForUser(userId),
                NotificationChannel.IN_APP, "order_created",
                variables("orderId", orderId, "userId", userId, "payableAmount", payableAmount));
    }

    private NotificationRequest buildOrderPaidNotification(AbstractDomainEvent event) {
        Object orderId = read(event, "orderId");
        Object userId = read(event, "userId");
        Object paymentNo = read(event, "paymentNo");
        Object paidAmount = read(event, "paidAmount");
        return base(event, "ORDER_PAID", String.valueOf(orderId), receiverForUser(userId),
                NotificationChannel.IN_APP, "order_paid",
                variables("orderId", orderId, "userId", userId, "paymentNo", paymentNo, "paidAmount", paidAmount));
    }

    private NotificationRequest buildPaymentSucceededNotification(AbstractDomainEvent event) {
        Object paymentNo = read(event, "paymentNo");
        Object orderId = read(event, "orderId");
        Object userId = read(event, "userId");
        Object paidAmount = read(event, "paidAmount");
        return base(event, "PAYMENT_SUCCEEDED", String.valueOf(paymentNo), receiverForUser(userId),
                NotificationChannel.IN_APP, "payment_succeeded",
                variables("paymentNo", paymentNo, "orderId", orderId, "userId", userId, "paidAmount", paidAmount));
    }

    private NotificationRequest buildRefundCompletedNotification(AbstractDomainEvent event) {
        Object refundNo = read(event, "refundNo");
        Object paymentNo = read(event, "paymentNo");
        Object orderId = read(event, "orderId");
        Object userId = read(event, "userId");
        Object refundAmount = read(event, "refundAmount");
        return base(event, "REFUND_COMPLETED", String.valueOf(refundNo), receiverForUser(userId),
                NotificationChannel.IN_APP, "refund_completed",
                variables("refundNo", refundNo, "paymentNo", paymentNo, "orderId", orderId,
                        "userId", userId, "refundAmount", refundAmount));
    }

    private NotificationRequest base(AbstractDomainEvent event, String bizType, String bizId, String receiver,
                                     NotificationChannel channel, String templateCode, Map<String, Object> variables) {
        return NotificationRequest.builder()
                .bizType(bizType)
                .bizId(bizId)
                .receiver(receiver)
                .channel(channel)
                .templateCode(templateCode)
                .variables(variables)
                .idempotencyKey("notification:" + event.getEventId())
                .build();
    }

    private String receiverForUser(Object userId) {
        return userId == null ? "unknown-user" : "user:" + userId;
    }

    private Object read(Object target, String property) {
        try {
            Method method = target.getClass().getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> variables(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) {
                values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return values;
    }

    private void persistFailure(AbstractDomainEvent event, Exception exception) {
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("Notification:" + event.getClass().getSimpleName());
            record.setEventPayload(serializeEvent(event));
            record.setErrorMessage(exception.getMessage());
            record.setLastError(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            record.setStatus(com.ecommerce.common.event.FailedEventStatus.PENDING);
            failedEventRecordRepository.save(record);
        } catch (Exception persistenceException) {
            log.error("Failed to persist notification failure record: {}", persistenceException.getMessage(), persistenceException);
        }
    }

    private String serializeEvent(AbstractDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
