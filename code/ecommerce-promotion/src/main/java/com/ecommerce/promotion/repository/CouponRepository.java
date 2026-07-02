package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Appendix C coupons records.
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCouponCode(String couponCode);
}
