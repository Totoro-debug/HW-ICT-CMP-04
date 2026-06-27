package com.ecommerce.common.audit;

import java.time.LocalDateTime;

public class AuditLogItem {

    private final Long id;
    private final String operatorId;
    private final String operatorName;
    private final String operationType;
    private final String bizType;
    private final String bizId;
    private final String beforeState;
    private final String afterState;
    private final LocalDateTime operatedAt;
    private final String remark;

    public AuditLogItem(Long id, String operatorId, String operatorName, String operationType,
                        String bizType, String bizId, String beforeState, String afterState,
                        LocalDateTime operatedAt, String remark) {
        this.id = id;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.operationType = operationType;
        this.bizType = bizType;
        this.bizId = bizId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.operatedAt = operatedAt;
        this.remark = remark;
    }

    public static AuditLogItem from(AuditLog log) {
        return new AuditLogItem(log.getId(), log.getOperatorId(), log.getOperatorName(),
                log.getOperationType(), log.getBizType(), log.getBizId(), log.getBeforeState(),
                log.getAfterState(), log.getOperatedAt(), log.getRemark());
    }

    public Long getId() { return id; }
    public String getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getOperationType() { return operationType; }
    public String getBizType() { return bizType; }
    public String getBizId() { return bizId; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public LocalDateTime getOperatedAt() { return operatedAt; }
    public String getRemark() { return remark; }
}
