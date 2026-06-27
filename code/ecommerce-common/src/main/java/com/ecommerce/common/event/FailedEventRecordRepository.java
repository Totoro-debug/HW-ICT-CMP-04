package com.ecommerce.common.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for managing failed event records.
 */
@Repository
public interface FailedEventRecordRepository extends JpaRepository<FailedEventRecord, Long> {

    List<FailedEventRecord> findByStatus(FailedEventStatus status);
}
