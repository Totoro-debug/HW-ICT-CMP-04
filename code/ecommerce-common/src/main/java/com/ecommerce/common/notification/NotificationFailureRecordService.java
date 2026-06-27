package com.ecommerce.common.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationFailureRecordService {

    private final NotificationFailureRecordRepository repository;

    public NotificationFailureRecordService(NotificationFailureRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationFailureRecord recordFailure(NotificationRequest request, Exception exception) {
        NotificationFailureRecord record = new NotificationFailureRecord();
        if (request != null) {
            record.setBizType(request.getBizType());
            record.setBizId(request.getBizId());
            record.setReceiver(request.getReceiver());
            record.setChannel(request.getChannel() != null ? request.getChannel().name() : null);
            record.setTemplateCode(request.getTemplateCode());
            record.setIdempotencyKey(request.getIdempotencyKey());
        }
        record.setErrorMessage(exception != null ? exception.getMessage() : null);
        record.setFailedAt(LocalDateTime.now());
        return repository.save(record);
    }
}
