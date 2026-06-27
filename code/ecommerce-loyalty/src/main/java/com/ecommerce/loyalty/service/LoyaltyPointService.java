package com.ecommerce.loyalty.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.money.MoneyValidationUtil;
import com.ecommerce.common.integration.LoyaltyCommandService;
import com.ecommerce.common.integration.LoyaltyQueryService;
import com.ecommerce.common.integration.PointsRedeemEstimator;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Core service for points operations: query, earn, redeem, and estimate.
 *
 * <p>Implements both {@link LoyaltyQueryService} (reads) and
 * {@link LoyaltyCommandService} (writes).
 */
@Service
public class LoyaltyPointService implements LoyaltyQueryService, LoyaltyCommandService, PointsRedeemEstimator {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyPointService.class);

    /** 100 points = 1 yuan */
    private static final int POINTS_PER_YUAN = 100;

    /** Maximum redeemable points per order (10,000 points = 100 yuan) */
    private static final int MAX_REDEEM_POINTS = 10_000;

    /** Maximum redeem ratio: points deduction cannot exceed 50% of order amount */
    private static final BigDecimal MAX_REDEEM_RATIO = new BigDecimal("0.5");

    private static final int DEFAULT_EXPIRE_MONTHS = 12;

    private final LoyaltyAccountRepository accountRepository;
    private final PointsTransactionRepository transactionRepository;
    private final MemberBenefitService memberBenefitService;

    public LoyaltyPointService(LoyaltyAccountRepository accountRepository,
                               PointsTransactionRepository transactionRepository,
                               MemberBenefitService memberBenefitService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.memberBenefitService = memberBenefitService;
    }

    // ======================== LoyaltyQueryService ========================

    public int getAvailablePoints(Long userId) {
        LoyaltyAccount account = getAccount(userId);
        return account.getAvailablePoints();
    }

    @Override
    public int estimateRedeemPoints(BigDecimal orderAmount, Long userId) {
        MoneyValidationUtil.validatePayableAmount(orderAmount);
        LoyaltyAccount account = getAccount(userId);
        int available = account.getAvailablePoints();

        // 50% of order amount in points: orderAmount * 100 * 0.5
        int ratioCapped = orderAmount.multiply(new BigDecimal(POINTS_PER_YUAN))
                .multiply(MAX_REDEEM_RATIO)
                .setScale(0, RoundingMode.DOWN)
                .intValue();

        // min(available, MAX_REDEEM_POINTS, ratioCapped)
        return Math.min(Math.min(available, MAX_REDEEM_POINTS), ratioCapped);
    }

    public MemberLevel getMemberLevel(Long userId) {
        LoyaltyAccount account = getAccount(userId);
        return account.getMemberLevel();
    }

    public BigDecimal getMemberMultiplier(Long userId) {
        LoyaltyAccount account = getAccount(userId);
        return memberBenefitService.getPointsMultiplier(account.getMemberLevel());
    }

    // ======================== LoyaltyCommandService ========================

    @Override
    @Transactional
    public int earnPaymentPoints(Long userId, BigDecimal orderAmount, double activityMultiplier) {
        int points = calcOrderPoints(orderAmount, userId, BigDecimal.valueOf(activityMultiplier));
        if (points <= 0) {
            return 0;
        }
        earnPoints(userId, points, "ORDER_PAYMENT", buildFallbackRedeemKey(userId, orderAmount, points), "Order payment reward");
        return points;
    }

    @Override
    @Transactional
    public int redeemPoints(Long userId, int points, BigDecimal orderAmount) {
        return redeemPoints(userId, points, orderAmount, "ORDER_REDEEM",
                buildFallbackRedeemKey(userId, orderAmount, points));
    }

    @Transactional
    public int redeemPoints(Long userId, int points, BigDecimal orderAmount, String bizType, String bizId) {
        validateIdempotencyKey(bizType, bizId);
        java.util.Optional<PointsTransaction> existing = transactionRepository.findFirstByUserIdAndTypeAndBizTypeAndBizId(
                userId, PointsTransactionType.REDEEM, bizType, bizId);
        if (existing != null && existing.isPresent()) {
            return Math.abs(existing.get().getAmount());
        }
        return doRedeemPoints(userId, points, orderAmount, bizType, bizId);
    }

    private int doRedeemPoints(Long userId, int points, BigDecimal orderAmount, String bizType, String bizId) {
        MoneyValidationUtil.validatePayableAmount(orderAmount);
        LoyaltyAccount account = getAccount(userId);

        // Apply 10,000 cap and 50% cap
        int maxRedeemable = estimateRedeemPoints(orderAmount, userId);
        int actual = Math.min(points, maxRedeemable);

        if (actual <= 0) {
            return 0;
        }

        account.setAvailablePoints(account.getAvailablePoints() - actual);
        account.setRedeemedPoints(account.getRedeemedPoints() + actual);
        account.setTotalPoints(Math.max(0, account.getTotalPoints() - actual));
        accountRepository.save(account);

        recordTransaction(userId, PointsTransactionType.REDEEM, -actual, account.getAvailablePoints(),
                bizType, bizId, "Points redeem, deducted " + actual + " points");

        log.info("Redeemed {} points for userId={}, balance={}", actual, userId, account.getAvailablePoints());
        return actual;
    }

    @Override
    @Transactional
    public void freezePoints(Long userId, int points, String bizType, String bizId, String description) {
        validatePositivePoints(points);
        validateIdempotencyKey(bizType, bizId);
        if (hasProcessed(userId, PointsTransactionType.FREEZE, bizType, bizId)) {
            return;
        }
        LoyaltyAccount account = getAccount(userId);
        if (account.getAvailablePoints() < points) {
            throw new BusinessException("LOYALTY_POINTS_NOT_ENOUGH", "Available points are not enough to freeze");
        }
        account.setAvailablePoints(account.getAvailablePoints() - points);
        account.setFrozenPoints(account.getFrozenPoints() + points);
        accountRepository.save(account);
        recordTransaction(userId, PointsTransactionType.FREEZE, -points, account.getAvailablePoints(),
                bizType, bizId, description);
    }

    @Override
    @Transactional
    public void unfreezePoints(Long userId, int points, String bizType, String bizId, String description) {
        validatePositivePoints(points);
        validateIdempotencyKey(bizType, bizId);
        if (hasProcessed(userId, PointsTransactionType.UNFREEZE, bizType, bizId)) {
            return;
        }
        LoyaltyAccount account = getAccount(userId);
        if (account.getFrozenPoints() < points) {
            throw new BusinessException("LOYALTY_POINTS_NOT_ENOUGH", "Frozen points are not enough to unfreeze");
        }
        account.setFrozenPoints(account.getFrozenPoints() - points);
        account.setAvailablePoints(account.getAvailablePoints() + points);
        accountRepository.save(account);
        recordTransaction(userId, PointsTransactionType.UNFREEZE, points, account.getAvailablePoints(),
                bizType, bizId, description);
    }

    @Override
    @Transactional
    public void consumeFrozenPoints(Long userId, int points, String bizType, String bizId, String description) {
        validatePositivePoints(points);
        validateIdempotencyKey(bizType, bizId);
        if (hasProcessed(userId, PointsTransactionType.CONSUME_FROZEN, bizType, bizId)) {
            return;
        }
        LoyaltyAccount account = getAccount(userId);
        if (account.getFrozenPoints() < points) {
            throw new BusinessException("LOYALTY_POINTS_NOT_ENOUGH", "Frozen points are not enough to consume");
        }
        account.setFrozenPoints(account.getFrozenPoints() - points);
        account.setRedeemedPoints(account.getRedeemedPoints() + points);
        account.setTotalPoints(Math.max(0, account.getTotalPoints() - points));
        accountRepository.save(account);
        recordTransaction(userId, PointsTransactionType.CONSUME_FROZEN, -points, account.getAvailablePoints(),
                bizType, bizId, description);
    }

    @Override
    public void expirePoints() {
        // Delegated to PointsExpireService
    }

    // ======================== Domain methods ========================

    /**
     * Calculate order points.
     *
     * <p>The calculation multiplies the paid amount by the points-per-yuan
     * rate, member benefit multiplier, request activity multiplier, and runtime
     * configured activity multiplier.
     *
     * @param amount             the order payable amount
     * @param userId             the user ID
     * @param activityMultiplier promotional activity coefficient (default 1.0)
     * @return calculated points
     */
    public int calcOrderPoints(BigDecimal amount, Long userId, BigDecimal activityMultiplier) {
        MoneyValidationUtil.validatePayableAmount(amount);
        LoyaltyAccount account = getAccount(userId);
        BigDecimal levelMultiplier = memberBenefitService.getPointsMultiplier(account.getMemberLevel());
        BigDecimal configuredActivityMultiplier = BigDecimal.valueOf(
                RuntimeConfigRegistry.getDouble("loyalty.activity-multiplier", 1.0d));
        BigDecimal requestActivityMultiplier = activityMultiplier != null ? activityMultiplier : BigDecimal.ONE;
        BigDecimal points = amount.multiply(BigDecimal.valueOf(POINTS_PER_YUAN))
                .multiply(levelMultiplier)
                .multiply(requestActivityMultiplier)
                .multiply(configuredActivityMultiplier);
        return points.setScale(0, RoundingMode.DOWN).intValue();
    }

    public int calcOrderPoints(BigDecimal amount, Long userId, double activityMultiplier) {
        return calcOrderPoints(amount, userId, BigDecimal.valueOf(activityMultiplier));
    }

    /**
     * Award points to a user and record the transaction.
     *
     * @param userId      the user ID
     * @param points      number of points to add
     * @param bizType     business type (e.g. "ORDER", "REVIEW")
     * @param bizId       business entity ID
     * @param description human-readable description
     */
    @Transactional
    public void earnPoints(Long userId, int points, String bizType, String bizId, String description) {
        // Fault injection check
        if (com.ecommerce.common.test.FaultInjectionRegistry.isActive("loyalty-award-points-failure")) {
            throw new RuntimeException("Fault injected: loyalty-award-points-failure");
        }

        if (bizType != null && bizId != null
                && hasProcessed(userId, PointsTransactionType.EARN, bizType, bizId)) {
            return;
        }

        LoyaltyAccount account = getAccount(userId);

        account.setTotalPoints(account.getTotalPoints() + points);
        account.setAvailablePoints(account.getAvailablePoints() + points);
        accountRepository.save(account);

        PointsTransaction tx = new PointsTransaction();
        tx.setUserId(userId);
        tx.setType(PointsTransactionType.EARN);
        tx.setAmount(points);
        tx.setBalance(account.getAvailablePoints());
        tx.setBizType(bizType);
        tx.setBizId(bizId);
        tx.setDescription(description);
        tx.setExpiresAt(SystemClockService.now().plusMonths(DEFAULT_EXPIRE_MONTHS));
        transactionRepository.save(tx);

        log.info("Earned {} points for userId={}, balance={}", points, userId, account.getAvailablePoints());
    }

    // ======================== Helpers ========================

    /**
     * Get the loyalty account for a user, or create one with defaults.
     */
    private LoyaltyAccount getAccount(Long userId) {
        return accountRepository.findByUserId(userId)
                .map(this::applyConfigOverrides)
                .orElseGet(() -> createDefaultAccount(userId));
    }

    private LoyaltyAccount createDefaultAccount(Long userId) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        int initialPoints = RuntimeConfigRegistry.getInt("loyalty.points", 0);
        account.setTotalPoints(initialPoints);
        account.setAvailablePoints(initialPoints);
        account.setFrozenPoints(0);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        account.setMemberLevel(resolveConfiguredMemberLevel(MemberLevel.NORMAL));
        account.setAnnualConsumption(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    private LoyaltyAccount applyConfigOverrides(LoyaltyAccount account) {
        boolean changed = false;
        MemberLevel configuredLevel = resolveConfiguredMemberLevel(account.getMemberLevel());
        if (configuredLevel != account.getMemberLevel()) {
            account.setMemberLevel(configuredLevel);
            changed = true;
        }
        return changed ? accountRepository.save(account) : account;
    }

    private MemberLevel resolveConfiguredMemberLevel(MemberLevel fallback) {
        String configured = RuntimeConfigRegistry.getString("loyalty.member-level", null);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return MemberLevel.valueOf(configured.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void validatePositivePoints(int points) {
        if (points <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "points must be greater than zero");
        }
    }

    private void validateIdempotencyKey(String bizType, String bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null || bizId.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "bizType and bizId are required for idempotent points operation");
        }
    }

    private boolean hasProcessed(Long userId, PointsTransactionType type, String bizType, String bizId) {
        return transactionRepository.existsByUserIdAndTypeAndBizTypeAndBizId(userId, type, bizType, bizId);
    }

    private String buildFallbackRedeemKey(Long userId, BigDecimal orderAmount, int points) {
        String normalizedAmount = orderAmount == null ? "null" : orderAmount.stripTrailingZeros().toPlainString();
        return userId + ":" + normalizedAmount + ":" + points;
    }

    private void recordTransaction(Long userId, PointsTransactionType type, int amount, int balance,
                                   String bizType, String bizId, String description) {
        PointsTransaction tx = new PointsTransaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalance(balance);
        tx.setBizType(bizType);
        tx.setBizId(bizId);
        tx.setDescription(description);
        tx.setExpiresAt(null);
        transactionRepository.save(tx);
    }

    /**
     * Exposed so the controller can build a points response.
     */
    public LoyaltyAccount getAccountByUserId(Long userId) {
        return getAccount(userId);
    }
}
