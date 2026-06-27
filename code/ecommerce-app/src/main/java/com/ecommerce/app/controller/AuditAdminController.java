package com.ecommerce.app.controller;

import com.ecommerce.common.audit.AuditLogItem;
import com.ecommerce.common.audit.AuditLogQuery;
import com.ecommerce.common.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditAdminController {

    private static final Logger log = LoggerFactory.getLogger(AuditAdminController.class);

    private final AuditLogService auditLogService;

    public AuditAdminController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        log.info("Querying audit logs, operatorId={}, operationType={}, bizType={}, bizId={}",
                operatorId, operationType, bizType, bizId);

        AuditLogQuery query = new AuditLogQuery();
        query.setOperatorId(operatorId);
        query.setOperationType(operationType);
        query.setBizType(bizType);
        query.setBizId(bizId);
        query.setFrom(from);
        query.setTo(to);

        List<AuditLogItem> records = auditLogService.find(query);
        return ResponseEntity.ok(Map.of(
                "count", records.size(),
                "records", records
        ));
    }
}
