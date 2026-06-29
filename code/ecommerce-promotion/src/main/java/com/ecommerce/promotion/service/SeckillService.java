package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.entity.SeckillPurchaseRecord;
import com.ecommerce.promotion.repository.SeckillPurchaseRecordRepository;
import com.ecommerce.promotion.repository.SeckillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for managing and validating seckill (flash-sale) activities.
 */
@Service
public class SeckillService {

    private final SeckillRepository seckillRepository;
    private final SeckillPurchaseRecordRepository seckillPurchaseRecordRepository;

    public SeckillService(SeckillRepository seckillRepository,
                          SeckillPurchaseRecordRepository seckillPurchaseRecordRepository) {
        this.seckillRepository = seckillRepository;
        this.seckillPurchaseRecordRepository = seckillPurchaseRecordRepository;
    }

    /**
     * Create a new seckill activity. ADMIN only.
     */
    @Transactional
    public SeckillActivity create(SeckillActivity activity) {
        if (activity.getStartTime() != null && activity.getEndTime() != null
                && !activity.getEndTime().isAfter(activity.getStartTime())) {
            throw new ValidationException("endTime", "End time must be after start time");
        }
        if (activity.getSeckillPrice() == null
                || activity.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("seckillPrice", "must be greater than 0");
        }
        activity.setSeckillPrice(MonetaryUtil.roundToCent(activity.getSeckillPrice()));
        activity.setSoldQuantity(0);
        activity.setStatus("ACTIVE");
        return seckillRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public SeckillActivity validateSeckill(Long skuId) {
        return validateSeckill(skuId, null, 1);
    }

    /**
     * Validate a seckill purchase by sku, user and quantity.
     */
    @Transactional(readOnly = true)
    public SeckillActivity validateSeckill(Long skuId, Long userId, Integer quantity) {
        SeckillActivity activity = seckillRepository.findBySkuIdAndStatus(skuId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("SeckillActivity for SKU", skuId));

        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BusinessException("COUPON_EXPIRED",
                    "Seckill activity has not started yet");
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BusinessException("COUPON_EXPIRED",
                    "Seckill activity has already ended");
        }

        int requestQuantity = quantity == null || quantity <= 0 ? 1 : quantity;
        int availableStock = (activity.getStockQuantity() != null ? activity.getStockQuantity() : 0)
                - (activity.getSoldQuantity() != null ? activity.getSoldQuantity() : 0);
        if (availableStock < requestQuantity) {
            throw new ConflictException("Seckill stock has been exhausted");
        }

        if (userId != null && activity.getPerUserLimit() != null) {
            int purchasedQuantity = seckillPurchaseRecordRepository.sumQuantityByActivityIdAndUserId(
                    activity.getId(), userId);
            if (purchasedQuantity + requestQuantity > activity.getPerUserLimit()) {
                throw new ConflictException("Seckill per-user limit exceeded");
            }
        }

        return activity;
    }

    /**
     * Record a successful seckill purchase.
     */
    @Transactional
    public void recordPurchase(Long activityId) {
        recordPurchase(activityId, null, null, 1, null);
    }

    @Transactional
    public void recordPurchase(Long activityId, Long userId, Long skuId, Integer quantity, Long orderId) {
        SeckillActivity activity = seckillRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("SeckillActivity", activityId));

        int purchaseQuantity = quantity == null || quantity <= 0 ? 1 : quantity;
        int sold = activity.getSoldQuantity() != null ? activity.getSoldQuantity() : 0;
        activity.setSoldQuantity(sold + purchaseQuantity);
        seckillRepository.save(activity);

        if (userId != null) {
            SeckillPurchaseRecord record = new SeckillPurchaseRecord();
            record.setActivityId(activity.getId());
            record.setUserId(userId);
            record.setSkuId(skuId != null ? skuId : activity.getSkuId());
            record.setQuantity(purchaseQuantity);
            record.setOrderId(orderId);
            seckillPurchaseRecordRepository.save(record);
        }
    }
}
