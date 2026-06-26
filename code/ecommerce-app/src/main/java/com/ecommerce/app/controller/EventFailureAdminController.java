package com.ecommerce.app.controller;

import com.ecommerce.common.event.FailedEventRecordItem;
import com.ecommerce.common.event.FailedEventRecordQueryService;
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

    public EventFailureAdminController(FailedEventRecordQueryService failedEventRecordQueryService) {
        this.failedEventRecordQueryService = failedEventRecordQueryService;
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
}
