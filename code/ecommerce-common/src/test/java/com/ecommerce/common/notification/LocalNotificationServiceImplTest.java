package com.ecommerce.common.notification;

import com.ecommerce.common.test.FaultInjectionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalNotificationServiceImpl")
class LocalNotificationServiceImplTest {

    @Mock
    private MockMailSender mockMailSender;

    @Mock
    private MockSmsSender mockSmsSender;

    @Mock
    private NotificationFailureRecordService failureRecordService;

    private LocalNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LocalNotificationServiceImpl(mockMailSender, mockSmsSender, failureRecordService);
        LocalNotificationServiceImpl.clearRecords();
        NotificationRecordService.clear();
        FaultInjectionRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        LocalNotificationServiceImpl.clearRecords();
        NotificationRecordService.clear();
        FaultInjectionRegistry.clear();
    }

    @Test
    @DisplayName("sends notification only once when duplicate requests share the same idempotency key")
    void testSend_notificationWithIdempotencyKey_onlySentOnce() {
        NotificationRequest request = request("ORDER", "ORD-001", "user@example.com",
                NotificationChannel.EMAIL, "ORDER_CONFIRMATION", "idem-key-123", Map.of());

        service.send(request);
        service.send(request);
        service.send(request);

        verify(mockMailSender, times(1))
                .sendEmail(eq("user@example.com"), anyString(), anyString());
        assertThat(LocalNotificationServiceImpl.getRecords()).hasSize(1);
        assertThat(NotificationRecordService.getAll()).hasSize(1);
    }

    @Test
    @DisplayName("sends notification separately for requests with different idempotency keys")
    void testSend_differentKeys_sendSeparately() {
        NotificationRequest request1 = request("ORDER", "ORD-001", "user@example.com",
                NotificationChannel.EMAIL, "TEMPLATE_1", "key-A", Map.of());
        NotificationRequest request2 = request("ORDER", "ORD-002", "user@example.com",
                NotificationChannel.EMAIL, "TEMPLATE_2", "key-B", Map.of());

        service.send(request1);
        service.send(request2);

        verify(mockMailSender, times(2))
                .sendEmail(eq("user@example.com"), anyString(), anyString());
        assertThat(LocalNotificationServiceImpl.getRecords()).hasSize(2);
        assertThat(NotificationRecordService.getAll()).hasSize(2);
    }

    @Test
    @DisplayName("delegates to MockMailSender for EMAIL channel")
    void testSend_delegatesToMockMailSenderForEmail() {
        NotificationRequest request = request("ORDER", "ORD-003", "customer@example.com",
                NotificationChannel.EMAIL, "ORDER_SHIPPED", null, Map.of());

        service.send(request);

        verify(mockMailSender).sendEmail(
                eq("customer@example.com"),
                eq("[ORDER] Notification"),
                anyString());
        verifyNoInteractions(mockSmsSender);
    }

    @Test
    @DisplayName("delegates to MockSmsSender for SMS channel")
    void testSend_delegatesToMockSmsSenderForSms() {
        NotificationRequest request = request("PROMO", "PROMO-001", "+1234567890",
                NotificationChannel.SMS, "PROMO_OFFER", null, Map.of());

        service.send(request);

        verify(mockSmsSender).sendSms(eq("+1234567890"), anyString());
        verifyNoInteractions(mockMailSender);
    }

    @Test
    @DisplayName("handles IN_APP channel by logging without delegating to mail or SMS senders")
    void testSend_inAppChannel_logsButUsesNoSender() {
        NotificationRequest request = request("SYSTEM", "SYS-001", "user123",
                NotificationChannel.IN_APP, "WELCOME", null, Map.of());

        service.send(request);

        verifyNoInteractions(mockMailSender, mockSmsSender);
        assertThat(LocalNotificationServiceImpl.getRecords()).hasSize(1);
        assertThat(NotificationRecordService.getAll()).hasSize(1);
    }

    @Test
    @DisplayName("ignores null NotificationRequest without throwing exception")
    void testSend_nullRequest_isIgnoredSafely() {
        service.send(null);

        verifyNoInteractions(mockMailSender, mockSmsSender, failureRecordService);
        assertThat(LocalNotificationServiceImpl.getRecords()).isEmpty();
        assertThat(NotificationRecordService.getAll()).isEmpty();
    }

    @Test
    @DisplayName("request without idempotency key is sent every time it is called")
    void testSend_noIdempotencyKey_sendsEveryTime() {
        NotificationRequest request = request("ORDER", "ORD-005", "repeat@example.com",
                NotificationChannel.EMAIL, "ALERT", null, Map.of());

        service.send(request);
        service.send(request);

        verify(mockMailSender, times(2))
                .sendEmail(eq("repeat@example.com"), anyString(), anyString());
        assertThat(LocalNotificationServiceImpl.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("notification for EMAIL channel uses subject format [bizType] Notification")
    void testSend_emailSubjectContainsBizType() {
        NotificationRequest request = request("PAYMENT", "PAY-100", "user@example.com",
                NotificationChannel.EMAIL, "PAYMENT_RECEIVED", null, Map.of());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

        service.send(request);

        verify(mockMailSender).sendEmail(anyString(), subjectCaptor.capture(), anyString());
        assertThat(subjectCaptor.getValue()).isEqualTo("[PAYMENT] Notification");
    }

    @Test
    @DisplayName("rendered template body includes template code and variables for EMAIL channel")
    void testSend_templateBodyIncludesTemplateCodeAndVariables() {
        NotificationRequest request = request("ORDER", "ORD-007", "user@example.com",
                NotificationChannel.EMAIL, "ORDER_CONFIRMATION", null,
                Map.of("orderId", "ORD-007", "amount", "99.99"));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        service.send(request);

        verify(mockMailSender).sendEmail(anyString(), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertThat(body).startsWith("[ORDER_CONFIRMATION]");
        assertThat(body).contains("orderId");
        assertThat(body).contains("99.99");
    }

    @Test
    @DisplayName("records failure but does not throw when sending fails")
    void testSend_failureIsIsolatedAndNoSuccessRecordWritten() {
        FaultInjectionRegistry.add("notification-send-failure");
        NotificationRequest request = request("ORDER", "ORD-008", "user@example.com",
                NotificationChannel.EMAIL, "ORDER_CONFIRMATION", "failure-key", Map.of("orderId", "ORD-008"));

        assertThatCode(() -> service.send(request)).doesNotThrowAnyException();

        verify(failureRecordService).recordFailure(eq(request), any(Exception.class));
        verify(mockMailSender, never()).sendEmail(anyString(), anyString(), anyString());
        assertThat(LocalNotificationServiceImpl.getRecords()).isEmpty();
        assertThat(NotificationRecordService.getAll()).isEmpty();
    }

    private NotificationRequest request(String bizType, String bizId, String receiver,
                                        NotificationChannel channel, String templateCode,
                                        String idempotencyKey, Map<String, Object> variables) {
        NotificationRequest request = new NotificationRequest();
        request.setBizType(bizType);
        request.setBizId(bizId);
        request.setReceiver(receiver);
        request.setChannel(channel);
        request.setTemplateCode(templateCode);
        request.setVariables(variables);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }
}
