package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.SeckillPurchaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeckillPurchaseRecordRepository extends JpaRepository<SeckillPurchaseRecord, Long> {

    @Query("select coalesce(sum(r.quantity), 0) from SeckillPurchaseRecord r where r.activityId = :activityId and r.userId = :userId")
    int sumQuantityByActivityIdAndUserId(@Param("activityId") Long activityId, @Param("userId") Long userId);
}
