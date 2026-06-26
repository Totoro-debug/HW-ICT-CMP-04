package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Validates whether a coupon can be applied to an order.
 */
@Service
public class CouponValidator {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponValidator(CouponTemplateRepository couponTemplateRepository,
                            UserCouponRepository userCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * Validate that a coupon is applicable.
     */
    public void validate(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new BusinessException("COUPON_INVALID", "Coupon not found");
        }

        CouponTemplate template = couponTemplateRepository.findById(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("CouponTemplate not found"));

        if (userCoupon.getStatus() != CouponStatus.AVAILABLE) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon is not available");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon template is not active");
        }
        if (template.getStartTime() != null && now.isBefore(template.getStartTime())) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon is not yet effective");
        }
        if (template.getEndTime() != null && now.isAfter(template.getEndTime())) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon has expired");
        }
    }
}
