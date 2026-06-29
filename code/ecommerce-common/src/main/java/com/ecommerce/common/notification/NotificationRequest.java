package com.ecommerce.common.notification;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for sending a notification through the LocalNotificationService.
 * All business modules must use this DTO — never call MockMailSender or MockSmsSender directly.
 */
public class NotificationRequest {

    private String bizType;
    private String bizId;
    private String receiver;
    private NotificationChannel channel;
    private String templateCode;
    private Map<String, Object> variables;
    private String idempotencyKey;

    public NotificationRequest() {
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getVariablesOrDefault() {
        return variables != null ? variables : new HashMap<>();
    }
}
