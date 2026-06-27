package com.ecommerce.app.controller;

import com.ecommerce.common.event.FailedEventRecordItem;
import com.ecommerce.common.event.FailedEventRecordQueryService;
import com.ecommerce.common.event.FailedEventReplayResult;
import com.ecommerce.common.event.FailedEventReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/events")
public class EventFailureAdminController {

    private static final Logger log = LoggerFactory.getLogger(EventFailureAdminController.class);

    private final FailedEventRecordQueryService failedEventRecordQueryService;
    private final FailedEventReplayService failedEventReplayService;

    public EventFailureAdminController(FailedEventRecordQueryService failedEventRecordQueryService,
                                       FailedEventReplayService failedEventReplayService) {
        this.failedEventRecordQueryService = failedEventRecordQueryService;
        this.failedEventReplayService = failedEventReplayService;
    }

    @GetMapping("/failures")
    public ResponseEntity<Map<String, Object>> getFailures(
            @RequestParam(required = false) String eventType) {
        log.info("Querying event failures, eventType={}", eventType);

        List<FailedEventRecordItem> records = failedEventRecordQueryService.findFailures(eventType);

        return ResponseEntity.ok(Map.of(
                "count", records.size(),
                "records", records
        ));
    }

    @PostMapping("/failures/{id}/replay")
    public ResponseEntity<FailedEventReplayResult> replayFailure(@PathVariable Long id) {
        log.info("Replaying event failure, id={}", id);
        return ResponseEntity.ok(failedEventReplayService.replay(id));
    }

    @PostMapping("/failures/replay")
    public ResponseEntity<Map<String, Object>> replayPendingFailures(
            @RequestParam(required = false) String eventType) {
        log.info("Replaying pending event failures, eventType={}", eventType);
        List<FailedEventReplayResult> results = failedEventReplayService.replayPending(eventType);
        return ResponseEntity.ok(Map.of(
                "count", results.size(),
                "records", results
        ));
    }
}
