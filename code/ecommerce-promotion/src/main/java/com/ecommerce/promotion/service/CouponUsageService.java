package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes coupon usage records required by order creation transactions.
 */
@Service
public class CouponUsageService implements PromotionUsageCommandService {

    private final UserCouponRepository userCouponRepository;
    private final CouponValidator couponValidator;

    public CouponUsageService(UserCouponRepository userCouponRepository,
                              CouponValidator couponValidator) {
        this.userCouponRepository = userCouponRepository;
        this.couponValidator = couponValidator;
    }

    @Override
    @Transactional
    public void markCouponsUsed(Long userId, Long orderId, List<Long> userCouponIds) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return;
        }
        if (userId == null || orderId == null) {
            throw new BusinessException("VALIDATION_FAILED", "userId and orderId are required to use coupons");
        }

        LocalDateTime usedAt = LocalDateTime.now();
        for (Long userCouponId : userCouponIds) {
            UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                    .orElseThrow(() -> new ResourceNotFoundException("UserCoupon", userCouponId));
            if (!userId.equals(userCoupon.getUserId())) {
                throw new ConflictException("Coupon does not belong to the user");
            }
            couponValidator.validate(userCoupon);
            userCoupon.setStatus(CouponStatus.USED);
            userCoupon.setUsedOrderId(orderId);
            userCoupon.setUsedAt(usedAt);
            userCouponRepository.save(userCoupon);
        }
    }
}
