package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.promotion.dto.CouponCreateRequest;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponTemplateService")
class CouponTemplateServiceTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    private CouponTemplateService couponTemplateService;
    private CouponCreateRequest request;

    @BeforeEach
    void setUp() {
        couponTemplateService = new CouponTemplateService(couponTemplateRepository, new ObjectMapper());
        request = new CouponCreateRequest();
        request.setName("Amount off");
        request.setType(CouponType.AMOUNT_OFF);
        request.setDiscountValue(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("create: rounds persisted money amounts with HALF_UP")
    void create_roundsMoneyAmountsHalfUp() {
        request.setType(CouponType.THRESHOLD_OFF);
        request.setDiscountValue(new BigDecimal("10.005"));
        request.setThresholdAmount(new BigDecimal("100.005"));
        request.setMaxDiscount(new BigDecimal("20.005"));
        when(couponTemplateRepository.save(any(CouponTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        couponTemplateService.create(request);

        ArgumentCaptor<CouponTemplate> captor = ArgumentCaptor.forClass(CouponTemplate.class);
        verify(couponTemplateRepository).save(captor.capture());
        CouponTemplate saved = captor.getValue();
        assertThat(saved.getDiscountValue()).isEqualByComparingTo(new BigDecimal("10.01"));
        assertThat(saved.getThresholdAmount()).isEqualByComparingTo(new BigDecimal("100.01"));
        assertThat(saved.getMaxDiscount()).isEqualByComparingTo(new BigDecimal("20.01"));
    }

    @Test
    @DisplayName("create: keeps discount coupon rate precision")
    void create_keepsDiscountRatePrecision() {
        request.setType(CouponType.DISCOUNT);
        request.setDiscountValue(new BigDecimal("0.955"));
        when(couponTemplateRepository.save(any(CouponTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        couponTemplateService.create(request);

        ArgumentCaptor<CouponTemplate> captor = ArgumentCaptor.forClass(CouponTemplate.class);
        verify(couponTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getDiscountValue()).isEqualByComparingTo(new BigDecimal("0.955"));
    }

    @Test
    @DisplayName("create: rejects zero fixed amount coupon")
    void create_rejectsZeroFixedAmountCoupon() {
        request.setDiscountValue(BigDecimal.ZERO);

        assertThatThrownBy(() -> couponTemplateService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("discountValue");
    }

    @Test
    @DisplayName("create: rejects discount rate out of range")
    void create_rejectsDiscountRateOutOfRange() {
        request.setType(CouponType.DISCOUNT);
        request.setDiscountValue(BigDecimal.ONE);

        assertThatThrownBy(() -> couponTemplateService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Discount rate");
    }

    @Test
    @DisplayName("create: rejects threshold discount greater than threshold")
    void create_rejectsThresholdDiscountGreaterThanThreshold() {
        request.setType(CouponType.THRESHOLD_OFF);
        request.setDiscountValue(new BigDecimal("100.01"));
        request.setThresholdAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> couponTemplateService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("threshold amount");
    }
}
