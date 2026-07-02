package com.ecommerce.loyalty.repository;

import com.ecommerce.loyalty.entity.LoyaltyPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Appendix C loyalty_points records.
 */
@Repository
public interface LoyaltyPointRepository extends JpaRepository<LoyaltyPoint, Long> {

    List<LoyaltyPoint> findByUserId(Long userId);

    List<LoyaltyPoint> findByUserIdAndAvailablePointsGreaterThanOrderByExpireDateAscIdAsc(Long userId, int availablePoints);

    List<LoyaltyPoint> findByUserIdAndExpireDateLessThanEqualAndAvailablePointsGreaterThan(Long userId,
                                                                                           LocalDate expireDate,
                                                                                           int availablePoints);

    List<LoyaltyPoint> findByExpireDateLessThanEqualAndAvailablePointsGreaterThan(LocalDate expireDate,
                                                                                  int availablePoints);

    @Query("select coalesce(sum(lp.availablePoints), 0) from LoyaltyPoint lp where lp.userId = :userId")
    Integer sumAvailablePointsByUserId(Long userId);
}
