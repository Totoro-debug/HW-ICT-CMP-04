package com.ecommerce.loyalty.service;

import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.LoyaltyPoint;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.LoyaltyPointRepository;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles the periodic expiration of loyalty points.
 *
 * <p>Points expire after 12 calendar months.
 */
@Service
public class PointsExpireService {

    private static final Logger log = LoggerFactory.getLogger(PointsExpireService.class);

    private static final String EXPIRE_BIZ_TYPE = "POINTS_EXPIRE";

    private final PointsTransactionRepository transactionRepository;
    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyPointRepository loyaltyPointRepository;

    public PointsExpireService(PointsTransactionRepository transactionRepository,
                               LoyaltyAccountRepository accountRepository) {
        this(transactionRepository, accountRepository, null);
    }

    @Autowired
    public PointsExpireService(PointsTransactionRepository transactionRepository,
                               LoyaltyAccountRepository accountRepository,
                               LoyaltyPointRepository loyaltyPointRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.loyaltyPointRepository = loyaltyPointRepository;
    }

    /**
     * Runs the points expiration task.
     */
    @Transactional
    public void expire() {
        LocalDateTime now = SystemClockService.now();
        expireLoyaltyPointBuckets(null, now.toLocalDate());
        List<PointsTransaction> expiredEarns = transactionRepository
                .findByTypeAndExpiresAtLessThanEqual(PointsTransactionType.EARN, now);
        ExpireResult result = expireEarnTransactions(expiredEarns);
        log.info("Expired loyalty points: processedEarns={}, expiredPoints={}",
                result.processedEarns(), result.expiredPoints());
    }

    @Transactional
    public void expireForUser(Long userId) {
        LocalDateTime now = SystemClockService.now();
        expireLoyaltyPointBuckets(userId, now.toLocalDate());
        List<PointsTransaction> expiredEarns = transactionRepository
                .findByUserIdAndTypeAndExpiresAtLessThanEqual(userId, PointsTransactionType.EARN, now);
        ExpireResult result = expireEarnTransactions(expiredEarns);
        log.info("Expired loyalty points for userId={}: processedEarns={}, expiredPoints={}",
                userId, result.processedEarns(), result.expiredPoints());
    }

    private void expireLoyaltyPointBuckets(Long userId, LocalDate today) {
        if (loyaltyPointRepository == null) {
            return;
        }
        List<LoyaltyPoint> expiredBuckets = userId == null
                ? loyaltyPointRepository.findByExpireDateLessThanEqualAndAvailablePointsGreaterThan(today, 0)
                : loyaltyPointRepository.findByUserIdAndExpireDateLessThanEqualAndAvailablePointsGreaterThan(userId, today, 0);
        for (LoyaltyPoint bucket : expiredBuckets) {
            bucket.setAvailablePoints(0);
        }
        loyaltyPointRepository.saveAll(expiredBuckets);
    }

    private ExpireResult expireEarnTransactions(List<PointsTransaction> expiredEarns) {
        int processed = 0;
        int totalExpired = 0;
        for (PointsTransaction earnTx : expiredEarns) {
            String expireBizId = String.valueOf(earnTx.getId());
            if (transactionRepository.existsByTypeAndBizTypeAndBizId(
                    PointsTransactionType.EXPIRE, EXPIRE_BIZ_TYPE, expireBizId)) {
                continue;
            }

            LoyaltyAccount account = accountRepository.findByUserId(earnTx.getUserId())
                    .orElse(null);
            if (account == null) {
                markProcessedWithoutBalanceChange(earnTx, expireBizId);
                processed++;
                continue;
            }

            int expireAmount = Math.min(Math.max(earnTx.getAmount(), 0), account.getAvailablePoints());
            if (expireAmount > 0) {
                account.setAvailablePoints(account.getAvailablePoints() - expireAmount);
                account.setExpiredPoints(account.getExpiredPoints() + expireAmount);
                account.setTotalPoints(Math.max(0, account.getTotalPoints() - expireAmount));
                accountRepository.save(account);
                totalExpired += expireAmount;
            }

            PointsTransaction expireTx = new PointsTransaction();
            expireTx.setUserId(earnTx.getUserId());
            expireTx.setType(PointsTransactionType.EXPIRE);
            expireTx.setAmount(-expireAmount);
            expireTx.setBalance(account.getAvailablePoints());
            expireTx.setBizType(EXPIRE_BIZ_TYPE);
            expireTx.setBizId(expireBizId);
            expireTx.setDescription("Expired points from transaction " + expireBizId);
            expireTx.setExpiresAt(null);
            transactionRepository.save(expireTx);
            processed++;
        }
        return new ExpireResult(processed, totalExpired);
    }

    private void markProcessedWithoutBalanceChange(PointsTransaction earnTx, String expireBizId) {
        PointsTransaction expireTx = new PointsTransaction();
        expireTx.setUserId(earnTx.getUserId());
        expireTx.setType(PointsTransactionType.EXPIRE);
        expireTx.setAmount(0);
        expireTx.setBalance(0);
        expireTx.setBizType(EXPIRE_BIZ_TYPE);
        expireTx.setBizId(expireBizId);
        expireTx.setDescription("Expired points skipped because loyalty account was not found");
        expireTx.setExpiresAt(null);
        transactionRepository.save(expireTx);
    }

    private record ExpireResult(int processedEarns, int expiredPoints) {
    }
}
