package com.ecommerce.common.audit;

import com.ecommerce.common.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(AuditLogRecordRequest request) {
        validate(request);
        AuditLog log = new AuditLog();
        log.setOperatorId(request.getOperatorId());
        log.setOperatorName(request.getOperatorName());
        log.setOperationType(request.getOperationType());
        log.setBizType(request.getBizType());
        log.setBizId(request.getBizId());
        log.setBeforeState(request.getBeforeState());
        log.setAfterState(request.getAfterState());
        log.setOperatedAt(request.getOperatedAt() != null ? request.getOperatedAt() : LocalDateTime.now());
        log.setRemark(request.getRemark());
        return repository.save(log);
    }

    @Transactional
    public AuditLog record(String operatorId, String operatorName, String operationType,
                           String bizType, String bizId, String beforeState,
                           String afterState, String remark) {
        AuditLogRecordRequest request = new AuditLogRecordRequest();
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
        request.setOperationType(operationType);
        request.setBizType(bizType);
        request.setBizId(bizId);
        request.setBeforeState(beforeState);
        request.setAfterState(afterState);
        request.setRemark(remark);
        return record(request);
    }

    @Transactional(readOnly = true)
    public List<AuditLogItem> find(AuditLogQuery query) {
        Stream<AuditLog> stream = repository.findAll().stream();
        if (query != null) {
            if (hasText(query.getOperatorId())) {
                stream = stream.filter(log -> Objects.equals(query.getOperatorId(), log.getOperatorId()));
            }
            if (hasText(query.getOperationType())) {
                stream = stream.filter(log -> Objects.equals(query.getOperationType(), log.getOperationType()));
            }
            if (hasText(query.getBizType())) {
                stream = stream.filter(log -> Objects.equals(query.getBizType(), log.getBizType()));
            }
            if (hasText(query.getBizId())) {
                stream = stream.filter(log -> Objects.equals(query.getBizId(), log.getBizId()));
            }
            if (query.getFrom() != null) {
                stream = stream.filter(log -> !log.getOperatedAt().isBefore(query.getFrom()));
            }
            if (query.getTo() != null) {
                stream = stream.filter(log -> !log.getOperatedAt().isAfter(query.getTo()));
            }
        }
        return stream.map(AuditLogItem::from).toList();
    }

    private void validate(AuditLogRecordRequest request) {
        if (request == null) {
            throw new ValidationException("Audit log request must not be null");
        }
        if (!hasText(request.getOperatorId())) {
            throw new ValidationException("Audit operatorId must not be blank");
        }
        if (!hasText(request.getOperationType())) {
            throw new ValidationException("Audit operationType must not be blank");
        }
        if (!hasText(request.getBizId())) {
            throw new ValidationException("Audit bizId must not be blank");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
