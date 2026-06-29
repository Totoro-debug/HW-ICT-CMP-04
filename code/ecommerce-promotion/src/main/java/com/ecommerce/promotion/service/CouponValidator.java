package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates whether a coupon can be applied to an order.
 */
@Service
public class CouponValidator {

    private final CouponTemplateRepository couponTemplateRepository;
    private final ProductQueryService productQueryService;
    private final ObjectMapper objectMapper;

    public CouponValidator(CouponTemplateRepository couponTemplateRepository,
                           ProductQueryService productQueryService,
                           ObjectMapper objectMapper) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.productQueryService = productQueryService;
        this.objectMapper = objectMapper;
    }

    public CouponTemplate validate(UserCoupon userCoupon) {
        return validate(userCoupon, userCoupon != null ? userCoupon.getUserId() : null,
                null, Collections.emptyList());
    }

    /**
     * Validate that a coupon is applicable.
     */
    public CouponTemplate validate(UserCoupon userCoupon,
                                   Long requestUserId,
                                   BigDecimal currentAmount,
                                   List<Long> skuIds) {
        if (userCoupon == null) {
            throw new ResourceNotFoundException("UserCoupon", null);
        }

        CouponTemplate template = couponTemplateRepository.findById(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("CouponTemplate", userCoupon.getCouponTemplateId()));

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

        if (template.getThresholdAmount() != null
                && currentAmount != null
                && currentAmount.compareTo(template.getThresholdAmount()) < 0) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon threshold is not met");
        }

        validateProductApplicability(template, skuIds);

        if (requestUserId != null && !requestUserId.equals(userCoupon.getUserId())) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon does not belong to current user");
        }

        if (userCoupon.getStatus() == CouponStatus.USED) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon has already been used");
        }
        if (userCoupon.getStatus() != CouponStatus.AVAILABLE) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon is not available");
        }

        return template;
    }

    private void validateProductApplicability(CouponTemplate template, List<Long> skuIds) {
        List<Long> applicableProductIds = parseIdList(template.getApplicableProductIds());
        List<Long> applicableCategoryIds = parseIdList(template.getApplicableCategoryIds());
        if (applicableProductIds.isEmpty() && applicableCategoryIds.isEmpty()) {
            return;
        }
        if (skuIds == null || skuIds.isEmpty()) {
            throw new BusinessException("COUPON_EXPIRED", "Coupon is not applicable to current items");
        }

        Set<Long> requestedSkuIds = new HashSet<>(skuIds);
        boolean productMatched = !applicableProductIds.isEmpty()
                && applicableProductIds.stream().anyMatch(requestedSkuIds::contains);
        if (productMatched) {
            return;
        }

        if (!applicableCategoryIds.isEmpty()) {
            List<Long> itemCategoryIds = productQueryService.getCategoryIdsBySkuIds(skuIds);
            Set<Long> requestedCategoryIds = new HashSet<>(itemCategoryIds);
            boolean categoryMatched = applicableCategoryIds.stream().anyMatch(requestedCategoryIds::contains);
            if (categoryMatched) {
                return;
            }
        }

        throw new BusinessException("COUPON_EXPIRED", "Coupon is not applicable to current items");
    }

    private List<Long> parseIdList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
            return ids == null ? Collections.emptyList() : ids;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
