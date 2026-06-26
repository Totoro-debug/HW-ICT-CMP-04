package com.ecommerce.common.event;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Query service that encapsulates failed event record access inside common.
 */
@Service
public class FailedEventRecordQueryService {

    private final FailedEventRecordRepository failedEventRecordRepository;

    public FailedEventRecordQueryService(FailedEventRecordRepository failedEventRecordRepository) {
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    public List<FailedEventRecordItem> findFailures(String eventType) {
        return failedEventRecordRepository.findAll().stream()
                .filter(record -> eventType == null || eventType.isBlank() || eventType.equals(record.getEventType()))
                .map(FailedEventRecordItem::from)
                .toList();
    }
}
