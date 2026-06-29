package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponValidator")
class CouponValidatorTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private ProductQueryService productQueryService;

    private CouponValidator couponValidator;
    private UserCoupon userCoupon;
    private CouponTemplate template;

    @BeforeEach
    void setUp() {
        couponValidator = new CouponValidator(couponTemplateRepository, productQueryService, new ObjectMapper());

        userCoupon = new UserCoupon();
        userCoupon.setId(10L);
        userCoupon.setUserId(1L);
        userCoupon.setCouponTemplateId(1L);
        userCoupon.setCouponCode("CPN-TEST001");
        userCoupon.setStatus(CouponStatus.AVAILABLE);

        template = new CouponTemplate();
        template.setId(1L);
        template.setName("Active Coupon");
        template.setType(CouponType.AMOUNT_OFF);
        template.setDiscountValue(new BigDecimal("10.00"));
        template.setStatus("ACTIVE");
        template.setStartTime(LocalDateTime.now().minusDays(1));
        template.setEndTime(LocalDateTime.now().plusDays(1));
    }

    @Test
    @DisplayName("validate passes for valid coupon")
    void validate_passesForValidCoupon() {
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatCode(() -> couponValidator.validate(userCoupon, 1L, new BigDecimal("100.00"), List.of(101L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate checks expiry before used status")
    void validate_checksExpiryBeforeUsedStatus() {
        userCoupon.setStatus(CouponStatus.USED);
        template.setEndTime(LocalDateTime.now().minusMinutes(1));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> couponValidator.validate(userCoupon, 1L, new BigDecimal("100.00"), List.of(101L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("validate checks threshold before user restriction")
    void validate_checksThresholdBeforeUserRestriction() {
        userCoupon.setStatus(CouponStatus.USED);
        template.setThresholdAmount(new BigDecimal("200.00"));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> couponValidator.validate(userCoupon, 999L, new BigDecimal("100.00"), List.of(101L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    @DisplayName("validate checks product applicability before user restriction")
    void validate_checksProductApplicabilityBeforeUserRestriction() {
        template.setApplicableProductIds("[200,201]");
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> couponValidator.validate(userCoupon, 999L, new BigDecimal("100.00"), List.of(101L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not applicable");
    }

    @Test
    @DisplayName("validate supports category applicability")
    void validate_supportsCategoryApplicability() {
        template.setApplicableCategoryIds("[88]");
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(productQueryService.getCategoryIdsBySkuIds(List.of(101L))).thenReturn(List.of(88L));

        CouponTemplate validated = couponValidator.validate(userCoupon, 1L, new BigDecimal("100.00"), List.of(101L));
        assertThat(validated).isSameAs(template);
    }

    @Test
    @DisplayName("validate rejects null coupon")
    void validate_rejectsNullCoupon() {
        assertThatThrownBy(() -> couponValidator.validate(null, 1L, new BigDecimal("100.00"), List.of(101L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UserCoupon");
    }
}
