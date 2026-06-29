package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService")
class CouponServiceTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    @Captor
    private ArgumentCaptor<UserCoupon> userCouponCaptor;

    @Captor
    private ArgumentCaptor<CouponTemplate> templateCaptor;

    @Nested
    @DisplayName("calculateDiscount")
    class CalculateDiscount {

        private CouponTemplate discountCoupon;
        private CouponTemplate amountOffCoupon;
        private CouponTemplate thresholdCoupon;

        @BeforeEach
        void setUp() {
            discountCoupon = new CouponTemplate();
            discountCoupon.setType(CouponType.DISCOUNT);
            discountCoupon.setDiscountValue(new BigDecimal("0.8"));
            discountCoupon.setStatus("ACTIVE");

            amountOffCoupon = new CouponTemplate();
            amountOffCoupon.setType(CouponType.AMOUNT_OFF);
            amountOffCoupon.setDiscountValue(new BigDecimal("10.00"));
            amountOffCoupon.setStatus("ACTIVE");

            thresholdCoupon = new CouponTemplate();
            thresholdCoupon.setType(CouponType.THRESHOLD_OFF);
            thresholdCoupon.setDiscountValue(new BigDecimal("30.00"));
            thresholdCoupon.setThresholdAmount(new BigDecimal("300.00"));
            thresholdCoupon.setStatus("ACTIVE");
        }

        @Test
        @DisplayName("discount coupon uses originalPrice - originalPrice×discountValue")
        void discountCoupon_usesDesignFormula() {
            BigDecimal result = couponService.calculateDiscount(new BigDecimal("100.00"), discountCoupon);
            assertThat(result).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("discount coupon keeps maxDiscount cap")
        void discountCoupon_keepsMaxDiscountCap() {
            discountCoupon.setDiscountValue(new BigDecimal("0.5"));
            discountCoupon.setMaxDiscount(new BigDecimal("30.00"));

            BigDecimal result = couponService.calculateDiscount(new BigDecimal("100.00"), discountCoupon);
            assertThat(result).isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("amount off coupon caps at price")
        void amountOffCoupon_capsAtPrice() {
            amountOffCoupon.setDiscountValue(new BigDecimal("150.00"));
            BigDecimal result = couponService.calculateDiscount(new BigDecimal("100.00"), amountOffCoupon);
            assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("threshold coupon only works when threshold met")
        void thresholdCoupon_onlyWhenThresholdMet() {
            assertThat(couponService.calculateDiscount(new BigDecimal("350.00"), thresholdCoupon))
                    .isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(couponService.calculateDiscount(new BigDecimal("250.00"), thresholdCoupon))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("claim")
    class Claim {

        private CouponTemplate template;
        private final Long userId = 42L;
        private final Long templateId = 1L;

        @BeforeEach
        void setUp() {
            template = new CouponTemplate();
            template.setId(templateId);
            template.setName("Test Coupon");
            template.setType(CouponType.AMOUNT_OFF);
            template.setDiscountValue(new BigDecimal("10.00"));
            template.setStatus("ACTIVE");
            template.setTotalQuantity(100);
            template.setIssuedQuantity(0);
            template.setPerUserLimit(5);
        }

        @Test
        @DisplayName("claim persists user coupon and increments issued quantity")
        void claim_persistsCoupon() {
            when(couponTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
            when(userCouponRepository.countByUserIdAndCouponTemplateId(userId, templateId)).thenReturn(0L);
            when(couponTemplateRepository.save(any(CouponTemplate.class))).thenReturn(template);
            when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserCoupon result = couponService.claim(userId, templateId);

            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getCouponTemplateId()).isEqualTo(templateId);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.AVAILABLE);

            verify(couponTemplateRepository).save(templateCaptor.capture());
            assertThat(templateCaptor.getValue().getIssuedQuantity()).isEqualTo(1);
            verify(userCouponRepository).save(userCouponCaptor.capture());
            assertThat(userCouponCaptor.getValue().getCouponCode()).startsWith("CPN-");
        }

        @Test
        @DisplayName("claim rejects exhausted template")
        void claim_rejectsExhaustedTemplate() {
            template.setIssuedQuantity(100);
            when(couponTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

            assertThatThrownBy(() -> couponService.claim(userId, templateId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fully claimed");
            verify(userCouponRepository, never()).save(any(UserCoupon.class));
        }

        @Test
        @DisplayName("claim rejects missing template")
        void claim_rejectsMissingTemplate() {
            when(couponTemplateRepository.findById(templateId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.claim(userId, templateId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
