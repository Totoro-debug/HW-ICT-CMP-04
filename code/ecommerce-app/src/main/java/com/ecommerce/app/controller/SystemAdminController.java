package com.ecommerce.app.controller;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.common.test.SystemClockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/system")
public class SystemAdminController {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminController.class);

    @PutMapping("/configs/{key}")
    public ResponseEntity<Map<String, Object>> putConfig(@PathVariable String key,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        Object value = body == null ? null : body.get("value");
        if (value == null) {
            throw new ValidationException("value", "is required").addDetail("field", "value");
        }
        RuntimeConfigRegistry.put(key, value);
        log.info("Config set: {} = {}", key, value);
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @GetMapping("/configs/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String key) {
        Object value = RuntimeConfigRegistry.getOrDefault(key);
        if (value == null) {
            throw new ResourceNotFoundException("Runtime config", key).addDetail("key", key);
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/clock")
    public ResponseEntity<Map<String, Object>> setClock(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            throw new ValidationException("clock", "Either offsetMinutes or timestamp is required")
                    .addDetail("fields", "offsetMinutes,timestamp");
        }
        if (body.containsKey("offsetMinutes")) {
            Object offsetValue = body.get("offsetMinutes");
            if (!(offsetValue instanceof Number)) {
                throw new ValidationException("offsetMinutes", "must be a number")
                        .addDetail("field", "offsetMinutes");
            }
            long offset = ((Number) offsetValue).longValue();
            SystemClockService.setOffset(offset);
            log.info("Clock offset set to {} minutes", offset);
            return ResponseEntity.ok(Map.of("offsetMinutes", offset));
        } else if (body.containsKey("timestamp")) {
            Object timestampValue = body.get("timestamp");
            if (!(timestampValue instanceof String)) {
                throw new ValidationException("timestamp", "must be an ISO_LOCAL_DATE_TIME string")
                        .addDetail("field", "timestamp");
            }
            String timestamp = (String) timestampValue;
            try {
                LocalDateTime fixed = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                SystemClockService.setFixed(fixed);
                log.info("Clock fixed at {}", fixed);
                return ResponseEntity.ok(Map.of("timestamp", fixed.toString()));
            } catch (DateTimeParseException e) {
                throw new ValidationException("timestamp", "Invalid timestamp format, use ISO_LOCAL_DATE_TIME")
                        .addDetail("field", "timestamp");
            }
        }
        throw new ValidationException("clock", "Either offsetMinutes or timestamp is required")
                .addDetail("fields", "offsetMinutes,timestamp");
    }

    @DeleteMapping("/clock")
    public ResponseEntity<Map<String, Object>> resetClock() {
        SystemClockService.reset();
        log.info("Clock reset to system time");
        return ResponseEntity.ok(Map.of("reset", true));
    }
}
