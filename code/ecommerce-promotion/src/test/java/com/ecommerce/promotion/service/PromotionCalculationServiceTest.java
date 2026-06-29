package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionCalculationService")
class PromotionCalculationServiceTest {

    @Mock
    private FullReductionService fullReductionService;

    @Mock
    private CouponService couponService;

    @Mock
    private CouponValidator couponValidator;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private SeckillService seckillService;

    @InjectMocks
    private PromotionCalculationServiceImpl promotionCalculationService;

    private PromotionCalculateRequest request;
    private PromotionCalculateRequest.CalculateItem item;
    private CouponTemplate discountTemplate;
    private UserCoupon userCoupon;

    @BeforeEach
    void setUp() {
        item = new PromotionCalculateRequest.CalculateItem();
        item.setSkuId(1L);
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(1);

        request = new PromotionCalculateRequest();
        request.setItems(List.of(item));
        request.setUserId(1L);
        request.setCouponIds(List.of(1L));

        discountTemplate = new CouponTemplate();
        discountTemplate.setId(100L);
        discountTemplate.setName("80% Off");
        discountTemplate.setType(CouponType.DISCOUNT);
        discountTemplate.setDiscountValue(new BigDecimal("0.8"));
        discountTemplate.setStatus("ACTIVE");

        userCoupon = new UserCoupon();
        userCoupon.setId(1L);
        userCoupon.setUserId(1L);
        userCoupon.setCouponTemplateId(100L);
        userCoupon.setCouponCode("CPN-DISC80");
        userCoupon.setStatus(CouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("calculate uses fullReduction -> coupon -> member order")
    void calculate_usesRequiredStackingOrder() {
        when(seckillService.validateSeckill(1L)).thenThrow(new RuntimeException("not seckill"));
        when(fullReductionService.calculateBestReduction(new BigDecimal("300.00")))
                .thenReturn(Optional.of(new BigDecimal("30.00")));

        PromotionCalculateRequest.CalculateItem item2 = new PromotionCalculateRequest.CalculateItem();
        item2.setSkuId(2L);
        item2.setPrice(new BigDecimal("200.00"));
        item2.setQuantity(1);
        request.setItems(List.of(item, item2));

        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));
        when(couponValidator.validate(eq(userCoupon), eq(1L), eq(new BigDecimal("270.00")), any()))
                .thenReturn(discountTemplate);
        when(couponService.calculateDiscount(new BigDecimal("270.00"), discountTemplate))
                .thenReturn(new BigDecimal("54.00"));

        PromotionCalculateResponse response = promotionCalculationService.calculate(request);

        assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(response.getCouponDiscount()).isEqualByComparingTo(new BigDecimal("54.00"));
        assertThat(response.getMemberDiscount()).isEqualByComparingTo(new BigDecimal("10.80"));
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("205.20"));
    }

    @Test
    @DisplayName("calculate excludes active seckill price items from full reduction base")
    void calculate_excludesSeckillItemsFromFullReductionBase() {
        PromotionCalculateRequest.CalculateItem regularItem = new PromotionCalculateRequest.CalculateItem();
        regularItem.setSkuId(11L);
        regularItem.setPrice(new BigDecimal("100.00"));
        regularItem.setQuantity(1);

        PromotionCalculateRequest.CalculateItem seckillItem = new PromotionCalculateRequest.CalculateItem();
        seckillItem.setSkuId(22L);
        seckillItem.setPrice(new BigDecimal("100.00"));
        seckillItem.setQuantity(1);

        request.setItems(List.of(regularItem, seckillItem));
        request.setUserId(null);
        request.setCouponIds(null);

        SeckillActivity seckillActivity = new SeckillActivity();
        seckillActivity.setId(1L);
        seckillActivity.setSkuId(22L);
        seckillActivity.setSeckillPrice(new BigDecimal("100.00"));
        seckillActivity.setStockQuantity(10);
        seckillActivity.setSoldQuantity(1);
        seckillActivity.setStatus("ACTIVE");

        when(seckillService.validateSeckill(11L)).thenThrow(new RuntimeException("not seckill"));
        when(seckillService.validateSeckill(22L)).thenReturn(seckillActivity);
        when(fullReductionService.calculateBestReduction(new BigDecimal("100.00"))).thenReturn(Optional.empty());

        PromotionCalculateResponse response = promotionCalculationService.calculate(request);
        assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("calculate skips coupons when couponIds empty")
    void calculate_skipsCouponsWhenEmpty() {
        request.setCouponIds(Collections.emptyList());
        when(seckillService.validateSeckill(1L)).thenThrow(new RuntimeException("not seckill"));
        when(fullReductionService.calculateBestReduction(new BigDecimal("100.00")))
                .thenReturn(Optional.of(new BigDecimal("10.00")));

        PromotionCalculateResponse response = promotionCalculationService.calculate(request);

        assertThat(response.getCouponDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.getMemberDiscount()).isEqualByComparingTo(new BigDecimal("4.50"));
    }

    @Test
    @DisplayName("calculate caps discounts so final amount stays at least 0.01")
    void calculate_capsDiscountsToMinimumPayable() {
        request.setUserId(null);
        request.setCouponIds(null);
        when(seckillService.validateSeckill(1L)).thenThrow(new RuntimeException("not seckill"));
        when(fullReductionService.calculateBestReduction(new BigDecimal("100.00")))
                .thenReturn(Optional.of(new BigDecimal("100.00")));

        PromotionCalculateResponse response = promotionCalculationService.calculate(request);

        assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(response.getTotalDiscount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("calculate blocks zero item total as invalid payable amount")
    void calculate_zeroItemTotalThrowsOrderValidationException() {
        item.setPrice(BigDecimal.ZERO);
        request.setUserId(null);
        request.setCouponIds(null);
        when(seckillService.validateSeckill(1L)).thenThrow(new RuntimeException("not seckill"));
        when(fullReductionService.calculateBestReduction(BigDecimal.ZERO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionCalculationService.calculate(request))
                .isInstanceOf(OrderValidationException.class)
                .extracting("code")
                .isEqualTo("PAYABLE_AMOUNT_TOO_LOW");
    }
}
