package com.ecommerce.common.notification;

public class NotificationSendException extends RuntimeException {

    public NotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
