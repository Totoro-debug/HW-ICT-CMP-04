package com.ecommerce.promotion.service;

import com.ecommerce.common.integration.PromotionDiscountCalculator;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Primary calculation service used by cart and order modules to compute
 * the final payable amount after all promotions.
 */
@Service
public class PromotionCalculationService implements PromotionDiscountCalculator {

    private final FullReductionService fullReductionService;
    private final CouponService couponService;
    private final CouponValidator couponValidator;
    private final UserCouponRepository userCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    public PromotionCalculationService(FullReductionService fullReductionService,
                                        CouponService couponService,
                                        CouponValidator couponValidator,
                                        UserCouponRepository userCouponRepository,
                                        CouponTemplateRepository couponTemplateRepository) {
        this.fullReductionService = fullReductionService;
        this.couponService = couponService;
        this.couponValidator = couponValidator;
        this.userCouponRepository = userCouponRepository;
        this.couponTemplateRepository = couponTemplateRepository;
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
    public PromotionCalculateResponse calculate(PromotionCalculateRequest request) {
        BigDecimal itemTotal = computeItemTotal(request.getItems());

        StackingContext context = new StackingContext(itemTotal);

        context.applyMemberDiscount(calculateMemberDiscount(request.getUserId(), context.currentAmount()));
        context.applyFullReduction(fullReductionService.calculateBestReduction(context.currentAmount())
                .orElse(BigDecimal.ZERO));
        context.applyTierPrice(calculateTierPriceDiscount(request, context.currentAmount()));
        CouponApplicationResult couponResult = calculateCouponDiscount(request.getUserId(),
                request.getCouponIds(), context.currentAmount());
        context.applyCoupon(couponResult.discount());

        PromotionCalculateResponse response = new PromotionCalculateResponse();
        response.setItemTotal(itemTotal);
        response.setFullReductionDiscount(context.fullReductionDiscount());
        response.setCouponDiscount(context.couponDiscount());
        response.setMemberDiscount(context.memberDiscount());
        response.setTotalDiscount(context.totalDiscount());
        response.setFinalAmount(context.currentAmount());
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

    /**
     * Calculate member-level discount.
     * In a real implementation, this would look up the user's member level
     * and apply the corresponding discount rate.
     * For now, returns a fixed 5% for demonstration.
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

    /**
     * Internal tier-price mount point. There is currently no persisted rule source
     * and the frozen API exposes no tier-price fields, so this step is a no-op
     * extension point while keeping the calculation pipeline explicit.
     */
    private BigDecimal calculateTierPriceDiscount(PromotionCalculateRequest request,
                                                  BigDecimal currentAmount) {
        return BigDecimal.ZERO;
    }

    private CouponApplicationResult calculateCouponDiscount(Long userId, List<Long> couponIds,
                                                            BigDecimal currentAmount) {
        if (userId == null || couponIds == null || couponIds.isEmpty()
                || currentAmount == null || currentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return CouponApplicationResult.empty();
        }

        BigDecimal bestDiscount = BigDecimal.ZERO;
        PromotionCalculateResponse.ApplicableCoupon bestCoupon = null;

        for (Long couponId : couponIds) {
            Optional<UserCoupon> userCouponOpt = userCouponRepository.findById(couponId);
            if (!userCouponOpt.isPresent()) {
                continue;
            }
            UserCoupon userCoupon = userCouponOpt.get();
            if (!userId.equals(userCoupon.getUserId())) {
                continue;
            }

            couponValidator.validate(userCoupon);

            Optional<CouponTemplate> templateOpt =
                    couponTemplateRepository.findById(userCoupon.getCouponTemplateId());
            if (!templateOpt.isPresent()) {
                continue;
            }

            CouponTemplate template = templateOpt.get();
            BigDecimal discount = couponService.calculateDiscount(currentAmount, template);
            if (discount.compareTo(currentAmount) > 0) {
                discount = currentAmount;
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
     * Centralized stacking model: member discount -> full reduction -> tier price
     * extension point -> best single coupon. Each step is capped by the remaining
     * amount so discounts cannot drive payable amount below zero.
     */
    private static class StackingContext {
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
            if (discount.compareTo(currentAmount) > 0) {
                return currentAmount;
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
