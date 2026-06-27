package com.ecommerce.common.event;

import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FailedEventReplayService {

    private final FailedEventRecordRepository repository;
    private final Map<String, FailedEventReplayHandler> handlers;

    public FailedEventReplayService(FailedEventRecordRepository repository,
                                    List<FailedEventReplayHandler> replayHandlers) {
        this.repository = repository;
        this.handlers = replayHandlers.stream()
                .collect(Collectors.toMap(FailedEventReplayHandler::eventType, Function.identity(), (a, b) -> a));
    }

    @Transactional
    public FailedEventReplayResult replay(Long id) {
        FailedEventRecord record = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FailedEventRecord", String.valueOf(id)));
        record.setStatus(FailedEventStatus.REPLAYING);
        record.setRetryCount(record.getRetryCount() + 1);
        record.setRetried(true);
        record.setReplayedAt(LocalDateTime.now());
        repository.save(record);

        try {
            FailedEventReplayHandler handler = handlers.get(record.getEventType());
            if (handler == null) {
                throw new IllegalStateException("No replay handler registered for event type: " + record.getEventType());
            }
            handler.replay(record.getEventPayload());
            record.setStatus(FailedEventStatus.SUCCEEDED);
            record.setLastError(null);
            record.setErrorMessage(null);
            repository.save(record);
            return new FailedEventReplayResult(record.getId(), record.getStatus(), record.getRetryCount(), null);
        } catch (Exception ex) {
            record.setStatus(FailedEventStatus.FAILED);
            record.setLastError(ex.getMessage());
            record.setErrorMessage(ex.getMessage());
            repository.save(record);
            return new FailedEventReplayResult(record.getId(), record.getStatus(), record.getRetryCount(), ex.getMessage());
        }
    }

    @Transactional
    public List<FailedEventReplayResult> replayPending(String eventType) {
        return repository.findByStatus(FailedEventStatus.PENDING).stream()
                .filter(record -> eventType == null || eventType.isBlank() || eventType.equals(record.getEventType()))
                .map(record -> replay(record.getId()))
                .toList();
    }
}
