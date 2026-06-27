package com.ecommerce.user.event;

import com.ecommerce.common.event.AbstractDomainEvent;
import com.ecommerce.common.notification.NotificationRequest;

/**
 * Event published after user registration to send the welcome notification after commit.
 */
public class UserRegistrationNotificationEvent extends AbstractDomainEvent {

    private final Long userId;
    private final String email;
    private final NotificationRequest notificationRequest;

    public UserRegistrationNotificationEvent(Object source, Long userId, String email,
                                             NotificationRequest notificationRequest) {
        super(source);
        this.userId = userId;
        this.email = email;
        this.notificationRequest = notificationRequest;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public NotificationRequest getNotificationRequest() {
        return notificationRequest;
    }
}
