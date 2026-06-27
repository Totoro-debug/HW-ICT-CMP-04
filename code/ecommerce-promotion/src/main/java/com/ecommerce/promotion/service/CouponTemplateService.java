package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.promotion.dto.CouponCreateRequest;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Administrative service for managing coupon templates.
 */
@Service
public class CouponTemplateService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final ObjectMapper objectMapper;

    public CouponTemplateService(CouponTemplateRepository couponTemplateRepository,
                                  ObjectMapper objectMapper) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new coupon template. ADMIN only.
     */
    @Transactional
    public CouponTemplate create(CouponCreateRequest request) {
        validateCreateRequest(request);

        CouponTemplate template = new CouponTemplate();
        template.setName(request.getName());
        template.setType(request.getType());
        template.setDiscountValue(request.getType() == CouponType.DISCOUNT
                ? request.getDiscountValue()
                : roundNullable(request.getDiscountValue()));
        template.setThresholdAmount(roundNullable(request.getThresholdAmount()));
        template.setMaxDiscount(roundNullable(request.getMaxDiscount()));
        template.setTotalQuantity(request.getTotalQuantity());
        template.setIssuedQuantity(0);
        template.setStartTime(request.getStartTime());
        template.setEndTime(request.getEndTime());
        template.setPerUserLimit(request.getPerUserLimit() != null ? request.getPerUserLimit() : 1);
        template.setStatus("ACTIVE");

        if (request.getApplicableCategoryIds() != null && !request.getApplicableCategoryIds().isEmpty()) {
            template.setApplicableCategoryIds(toJson(request.getApplicableCategoryIds()));
        }
        if (request.getApplicableProductIds() != null && !request.getApplicableProductIds().isEmpty()) {
            template.setApplicableProductIds(toJson(request.getApplicableProductIds()));
        }

        return couponTemplateRepository.save(template);
    }

    /**
     * List all active coupon templates.
     */
    @Transactional(readOnly = true)
    public List<CouponTemplate> listActive() {
        return couponTemplateRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
    }

    private void validateCreateRequest(CouponCreateRequest request) {
        if (request.getType() == null) {
            throw new ValidationException("type", "Coupon type is required");
        }
        if (request.getType() == CouponType.DISCOUNT) {
            if (request.getDiscountValue() == null) {
                throw new ValidationException("discountValue", "Discount value is required for DISCOUNT coupon");
            }
            if (request.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0
                    || request.getDiscountValue().compareTo(BigDecimal.ONE) >= 0) {
                throw new ValidationException("discountValue", "Discount rate must be greater than 0 and less than 1");
            }
            validateNonNegative("maxDiscount", request.getMaxDiscount());
            return;
        }
        validatePositive("discountValue", request.getDiscountValue());
        if (request.getType() == CouponType.THRESHOLD_OFF) {
            validatePositive("thresholdAmount", request.getThresholdAmount());
            if (request.getDiscountValue().compareTo(request.getThresholdAmount()) > 0) {
                throw new ValidationException("discountValue", "Discount value must not exceed threshold amount");
            }
        }
        validateNonNegative("maxDiscount", request.getMaxDiscount());
    }

    private void validatePositive(String field, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(field, "must be greater than 0");
        }
    }

    private void validateNonNegative(String field, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(field, "must not be negative");
        }
    }

    private BigDecimal roundNullable(BigDecimal amount) {
        return amount != null ? MonetaryUtil.roundToCent(amount) : null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
