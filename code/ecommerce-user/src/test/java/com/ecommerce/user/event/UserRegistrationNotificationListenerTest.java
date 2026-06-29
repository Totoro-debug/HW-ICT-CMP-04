package com.ecommerce.user.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.FailedEventStatus;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserRegistrationNotificationListener")
class UserRegistrationNotificationListenerTest {

    @Test
    @DisplayName("sends activation notification after event")
    void testOnUserRegistrationNotification_sendsNotification() {
        LocalNotificationService notificationService = mock(LocalNotificationService.class);
        FailedEventRecordRepository failedEventRecordRepository = mock(FailedEventRecordRepository.class);
        UserRegistrationNotificationListener listener =
                new UserRegistrationNotificationListener(notificationService, failedEventRecordRepository);
        NotificationRequest request = notificationRequest();
        UserRegistrationNotificationEvent event =
                new UserRegistrationNotificationEvent(this, 1L, "newuser@example.com", request);

        listener.onUserRegistrationNotification(event);

        verify(notificationService).send(request);
    }

    @Test
    @DisplayName("persists failed event record and does not propagate notification failure")
    void testOnUserRegistrationNotification_failure_persistsFailedEventRecord() {
        LocalNotificationService notificationService = mock(LocalNotificationService.class);
        FailedEventRecordRepository failedEventRecordRepository = mock(FailedEventRecordRepository.class);
        when(failedEventRecordRepository.save(any(FailedEventRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        UserRegistrationNotificationListener listener =
                new UserRegistrationNotificationListener(notificationService, failedEventRecordRepository);
        NotificationRequest request = notificationRequest();
        UserRegistrationNotificationEvent event =
                new UserRegistrationNotificationEvent(this, 1L, "newuser@example.com", request);
        doThrow(new RuntimeException("mail unavailable")).when(notificationService).send(request);

        assertThatCode(() -> listener.onUserRegistrationNotification(event))
                .doesNotThrowAnyException();

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertThat(record.getEventType()).isEqualTo("UserRegistrationNotificationEvent");
        assertThat(record.getEventPayload())
                .contains("\"userId\":1", "\"email\":\"newuser@example.com\"", "\"templateCode\":\"EMAIL_ACTIVATION\"");
        assertThat(record.getErrorMessage()).isEqualTo("mail unavailable");
        assertThat(record.getLastError()).isEqualTo("mail unavailable");
        assertThat(record.getOccurredAt()).isNotNull();
        assertThat(record.isRetried()).isFalse();
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getStatus()).isEqualTo(FailedEventStatus.PENDING);
    }

    private NotificationRequest notificationRequest() {
        NotificationRequest request = new NotificationRequest();
        request.setBizType("EMAIL_ACTIVATION");
        request.setBizId("1");
        request.setReceiver("newuser@example.com");
        request.setChannel(NotificationChannel.EMAIL);
        request.setTemplateCode("EMAIL_ACTIVATION");
        request.setVariables(Map.of(
                "userId", 1L,
                "email", "newuser@example.com",
                "nickname", "NewUser",
                "activationToken", "token-123",
                "activationLink", "/api/v1/users/activate?token=token-123"));
        request.setIdempotencyKey("EMAIL_ACTIVATION:1");
        return request;
    }
}
