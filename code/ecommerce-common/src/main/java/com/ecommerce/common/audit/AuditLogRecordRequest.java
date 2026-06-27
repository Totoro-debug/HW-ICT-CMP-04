package com.ecommerce.common.audit;

import java.time.LocalDateTime;

public class AuditLogRecordRequest {

    private String operatorId;
    private String operatorName;
    private String operationType;
    private String bizType;
    private String bizId;
    private String beforeState;
    private String afterState;
    private LocalDateTime operatedAt;
    private String remark;

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String afterState) { this.afterState = afterState; }
    public LocalDateTime getOperatedAt() { return operatedAt; }
    public void setOperatedAt(LocalDateTime operatedAt) { this.operatedAt = operatedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
