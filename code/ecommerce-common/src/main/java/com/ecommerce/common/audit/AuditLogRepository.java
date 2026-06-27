package com.ecommerce.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOperationType(String operationType);

    List<AuditLog> findByBizTypeAndBizId(String bizType, String bizId);

    List<AuditLog> findByOperatorId(String operatorId);
}
