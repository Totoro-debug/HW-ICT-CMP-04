package com.ecommerce.promotion.service;

import com.ecommerce.common.integration.PromotionDiscountCalculator;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.common.money.MoneyValidationUtil;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.promotion.config.PromotionProperties;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of the promotion-provided calculation interface.
 */
@Service
public class PromotionCalculationServiceImpl implements PromotionCalculationService, PromotionDiscountCalculator {

    private final FullReductionService fullReductionService;
    private final CouponService couponService;
    private final CouponValidator couponValidator;
    private final UserCouponRepository userCouponRepository;
    private final SeckillService seckillService;
    private final PromotionProperties promotionProperties;

    public PromotionCalculationServiceImpl(FullReductionService fullReductionService,
                                           CouponService couponService,
                                           CouponValidator couponValidator,
                                           UserCouponRepository userCouponRepository,
                                           SeckillService seckillService,
                                           PromotionProperties promotionProperties) {
        this.fullReductionService = fullReductionService;
        this.couponService = couponService;
        this.couponValidator = couponValidator;
        this.userCouponRepository = userCouponRepository;
        this.seckillService = seckillService;
        this.promotionProperties = promotionProperties != null ? promotionProperties : new PromotionProperties();
    }

    @Override
    public BigDecimal calculateDiscount(Long userId, List<PromotionDiscountCalculator.Item> items,
                                        List<Long> couponIds) {
        PromotionCalculateRequest request = new PromotionCalculateRequest();
        request.setUserId(userId);
        request.setCouponIds(couponIds);
        request.setItems(items.stream()
                .map(item -> {
                    PromotionCalculateRequest.CalculateItem calculateItem =
                            new PromotionCalculateRequest.CalculateItem();
                    calculateItem.setSkuId(item.getSkuId());
                    calculateItem.setPrice(item.getPrice());
                    calculateItem.setQuantity(item.getQuantity());
                    return calculateItem;
                })
                .toList());
        PromotionCalculateResponse response = calculate(request);
        return response.getTotalDiscount() != null ? response.getTotalDiscount() : BigDecimal.ZERO;
    }

    /**
     * Calculate all applicable promotions for an order.
     */
    @Override
    public PromotionCalculateResponse calculate(PromotionCalculateRequest request) {
        BigDecimal itemTotal = computeItemTotal(request.getItems());
        BigDecimal fullReductionEligibleTotal = computeFullReductionEligibleTotal(request.getItems());

        StackingContext context = new StackingContext(itemTotal);

        CouponApplicationResult couponResult = CouponApplicationResult.empty();
        for (String step : promotionProperties.getStackOrder()) {
            couponResult = applyConfiguredStackingStep(step, request, context, fullReductionEligibleTotal, couponResult);
        }
        context.applyTierPrice(calculateTierPriceDiscount(request, context.currentAmount()));

        PromotionCalculateResponse response = new PromotionCalculateResponse();
        BigDecimal finalAmount = MonetaryUtil.roundToCent(context.currentAmount());
        MoneyValidationUtil.validatePayableAmount(finalAmount);

        response.setItemTotal(MonetaryUtil.roundToCent(itemTotal));
        response.setFullReductionDiscount(MonetaryUtil.roundToCent(context.fullReductionDiscount()));
        response.setCouponDiscount(MonetaryUtil.roundToCent(context.couponDiscount()));
        response.setMemberDiscount(MonetaryUtil.roundToCent(context.memberDiscount()));
        response.setTotalDiscount(MonetaryUtil.roundToCent(context.totalDiscount()));
        response.setFinalAmount(finalAmount);
        response.setApplicableCoupons(couponResult.applicableCoupons());

        return response;
    }

    private BigDecimal computeItemTotal(List<PromotionCalculateRequest.CalculateItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PromotionCalculateRequest.CalculateItem item : items) {
            BigDecimal lineTotal = MonetaryUtil.multiply(item.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            total = MonetaryUtil.add(total, lineTotal);
        }
        return total;
    }

    private BigDecimal computeFullReductionEligibleTotal(List<PromotionCalculateRequest.CalculateItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PromotionCalculateRequest.CalculateItem item : items) {
            if (item == null || isActiveSeckillPriceItem(item)) {
                continue;
            }
            BigDecimal lineTotal = MonetaryUtil.multiply(item.getPrice(), BigDecimal.valueOf(item.getQuantity()));
            total = MonetaryUtil.add(total, lineTotal);
        }
        return total;
    }

    private boolean isActiveSeckillPriceItem(PromotionCalculateRequest.CalculateItem item) {
        try {
            SeckillActivity activity = seckillService.validateSeckill(item.getSkuId());
            return activity.getSeckillPrice() != null
                    && item.getPrice() != null
                    && MonetaryUtil.roundToCent(activity.getSeckillPrice())
                    .compareTo(MonetaryUtil.roundToCent(item.getPrice())) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private CouponApplicationResult applyConfiguredStackingStep(String step,
                                                                 PromotionCalculateRequest request,
                                                                 StackingContext context,
                                                                 BigDecimal fullReductionEligibleTotal,
                                                                 CouponApplicationResult couponResult) {
        if (step == null) {
            return couponResult;
        }
        switch (step.trim().toUpperCase()) {
            case "FULL_REDUCTION" -> context.applyFullReduction(
                    fullReductionService.calculateBestReduction(fullReductionEligibleTotal).orElse(BigDecimal.ZERO));
            case "COUPON" -> {
                CouponApplicationResult result = calculateCouponDiscount(request.getUserId(),
                        request.getCouponIds(), request.getItems(), context.currentAmount());
                context.applyCoupon(result.discount());
                return result;
            }
            case "MEMBER_DISCOUNT" -> context.applyMemberDiscount(
                    calculateMemberDiscount(request.getUserId(), context.currentAmount()));
            default -> {
                return couponResult;
            }
        }
        return couponResult;
    }

    /**
     * Calculate member-level discount.
     */
    private BigDecimal calculateMemberDiscount(Long userId, BigDecimal amount) {
        if (userId == null || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal memberRate = RuntimeConfigRegistry.getBigDecimal(
                "member.discount-rate", new BigDecimal("0.95"));
        BigDecimal afterDiscount = MonetaryUtil.multiply(amount, memberRate);
        return MonetaryUtil.subtract(amount, afterDiscount);
    }

    private BigDecimal calculateTierPriceDiscount(PromotionCalculateRequest request,
                                                  BigDecimal currentAmount) {
        return BigDecimal.ZERO;
    }

    private CouponApplicationResult calculateCouponDiscount(Long userId,
                                                            List<Long> couponIds,
                                                            List<PromotionCalculateRequest.CalculateItem> items,
                                                            BigDecimal currentAmount) {
        if (userId == null || couponIds == null || couponIds.isEmpty()
                || currentAmount == null || currentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return CouponApplicationResult.empty();
        }

        List<Long> skuIds = items == null ? List.of() : items.stream()
                .map(PromotionCalculateRequest.CalculateItem::getSkuId)
                .toList();

        BigDecimal bestDiscount = BigDecimal.ZERO;
        PromotionCalculateResponse.ApplicableCoupon bestCoupon = null;

        for (Long couponId : couponIds) {
            Optional<UserCoupon> userCouponOpt = userCouponRepository.findById(couponId);
            if (!userCouponOpt.isPresent()) {
                continue;
            }
            UserCoupon userCoupon = userCouponOpt.get();

            CouponTemplate template = couponValidator.validate(userCoupon, userId, currentAmount, skuIds);

            BigDecimal discount = couponService.calculateDiscount(currentAmount, template);
            BigDecimal maxDiscount = currentAmount.subtract(MoneyValidationUtil.MIN_PAYABLE_AMOUNT);
            if (maxDiscount.compareTo(BigDecimal.ZERO) <= 0) {
                discount = BigDecimal.ZERO;
            } else if (discount.compareTo(maxDiscount) > 0) {
                discount = maxDiscount;
            }
            if (discount.compareTo(bestDiscount) > 0) {
                bestDiscount = discount;
                bestCoupon = toApplicableCoupon(userCoupon, template, discount);
            }
        }

        if (bestCoupon == null) {
            return CouponApplicationResult.empty();
        }
        return new CouponApplicationResult(bestDiscount, List.of(bestCoupon));
    }

    private PromotionCalculateResponse.ApplicableCoupon toApplicableCoupon(UserCoupon userCoupon,
                                                                           CouponTemplate template,
                                                                           BigDecimal discount) {
        PromotionCalculateResponse.ApplicableCoupon applicableCoupon =
                new PromotionCalculateResponse.ApplicableCoupon();
        applicableCoupon.setCouponId(userCoupon.getId());
        applicableCoupon.setCouponCode(userCoupon.getCouponCode());
        applicableCoupon.setName(template.getName());
        applicableCoupon.setDiscountAmount(discount);
        return applicableCoupon;
    }

    /**
     * Centralized stacking model: full reduction -> coupon -> member discount -> tier price.
     */
    private static class StackingContext {
        private static final BigDecimal MIN_PAYABLE = MoneyValidationUtil.MIN_PAYABLE_AMOUNT;

        private BigDecimal currentAmount;
        private BigDecimal memberDiscount = BigDecimal.ZERO;
        private BigDecimal fullReductionDiscount = BigDecimal.ZERO;
        private BigDecimal tierPriceDiscount = BigDecimal.ZERO;
        private BigDecimal couponDiscount = BigDecimal.ZERO;

        StackingContext(BigDecimal itemTotal) {
            this.currentAmount = itemTotal;
        }

        void applyMemberDiscount(BigDecimal discount) {
            memberDiscount = cap(discount);
            currentAmount = MonetaryUtil.subtract(currentAmount, memberDiscount);
        }

        void applyFullReduction(BigDecimal discount) {
            fullReductionDiscount = cap(discount);
            currentAmount = MonetaryUtil.subtract(currentAmount, fullReductionDiscount);
        }

        void applyTierPrice(BigDecimal discount) {
            tierPriceDiscount = cap(discount);
            currentAmount = MonetaryUtil.subtract(currentAmount, tierPriceDiscount);
        }

        void applyCoupon(BigDecimal discount) {
            couponDiscount = cap(discount);
            currentAmount = MonetaryUtil.subtract(currentAmount, couponDiscount);
        }

        BigDecimal currentAmount() {
            return currentAmount;
        }

        BigDecimal memberDiscount() {
            return memberDiscount;
        }

        BigDecimal fullReductionDiscount() {
            return fullReductionDiscount;
        }

        BigDecimal couponDiscount() {
            return couponDiscount;
        }

        BigDecimal totalDiscount() {
            return MonetaryUtil.add(MonetaryUtil.add(memberDiscount, fullReductionDiscount),
                    MonetaryUtil.add(tierPriceDiscount, couponDiscount));
        }

        private BigDecimal cap(BigDecimal discount) {
            if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal maxDiscount = currentAmount.subtract(MIN_PAYABLE);
            if (maxDiscount.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            if (discount.compareTo(maxDiscount) > 0) {
                return maxDiscount;
            }
            return discount;
        }
    }

    private record CouponApplicationResult(
            BigDecimal discount,
            List<PromotionCalculateResponse.ApplicableCoupon> applicableCoupons) {

        static CouponApplicationResult empty() {
            return new CouponApplicationResult(BigDecimal.ZERO, new ArrayList<>());
        }
    }
}
