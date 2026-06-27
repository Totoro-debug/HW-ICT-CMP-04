package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponUsageService")
class CouponUsageServiceTest {

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponValidator couponValidator;

    private CouponUsageService couponUsageService;
    private UserCoupon userCoupon;

    @BeforeEach
    void setUp() {
        couponUsageService = new CouponUsageService(userCouponRepository, couponValidator);
        userCoupon = new UserCoupon();
        userCoupon.setId(1L);
        userCoupon.setUserId(10L);
        userCoupon.setCouponTemplateId(100L);
        userCoupon.setCouponCode("CPN-USED");
        userCoupon.setStatus(CouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("markCouponsUsed: first use validates and marks coupon used")
    void markCouponsUsed_firstUseMarksCouponUsed() {
        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));

        couponUsageService.markCouponsUsed(10L, 20L, List.of(1L));

        verify(couponValidator).validate(userCoupon);
        verify(userCouponRepository).save(userCoupon);
    }

    @Test
    @DisplayName("markCouponsUsed: same order repeat is idempotent success")
    void markCouponsUsed_sameOrderRepeatIsIdempotentSuccess() {
        userCoupon.setStatus(CouponStatus.USED);
        userCoupon.setUsedOrderId(20L);
        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));

        couponUsageService.markCouponsUsed(10L, 20L, List.of(1L));

        verify(couponValidator, never()).validate(userCoupon);
        verify(userCouponRepository, never()).save(userCoupon);
    }

    @Test
    @DisplayName("markCouponsUsed: different order for used coupon conflicts")
    void markCouponsUsed_differentOrderConflicts() {
        userCoupon.setStatus(CouponStatus.USED);
        userCoupon.setUsedOrderId(99L);
        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));

        assertThatThrownBy(() -> couponUsageService.markCouponsUsed(10L, 20L, List.of(1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another order");

        verify(couponValidator, never()).validate(userCoupon);
        verify(userCouponRepository, never()).save(userCoupon);
    }
}
