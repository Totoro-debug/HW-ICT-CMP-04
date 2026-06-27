package com.ecommerce.common.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationFailureRecordRepository extends JpaRepository<NotificationFailureRecord, Long> {
}
