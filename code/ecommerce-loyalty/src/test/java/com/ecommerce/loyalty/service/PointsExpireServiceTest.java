package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.LoyaltyPoint;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.entity.PointsTransaction;
import com.ecommerce.loyalty.entity.PointsTransactionType;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import com.ecommerce.loyalty.repository.LoyaltyPointRepository;
import com.ecommerce.loyalty.repository.PointsTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PointsExpireService}.
 */
@ExtendWith(MockitoExtension.class)
class PointsExpireServiceTest {

    @Mock
    private PointsTransactionRepository transactionRepository;

    @Mock
    private LoyaltyAccountRepository accountRepository;

    @Mock
    private LoyaltyPointRepository loyaltyPointRepository;

    private PointsExpireService pointsExpireService;

    @BeforeEach
    void setUp() {
        lenient().when(loyaltyPointRepository.findByExpireDateLessThanEqualAndAvailablePointsGreaterThan(any(), eq(0))).thenReturn(List.of());
        lenient().when(loyaltyPointRepository.findByUserIdAndExpireDateLessThanEqualAndAvailablePointsGreaterThan(any(), any(), eq(0))).thenReturn(List.of());
        pointsExpireService = new PointsExpireService(transactionRepository, accountRepository, loyaltyPointRepository);
    }

    @Test
    void testExpire_deductsExpiredEarnAndRecordsTransaction() {
        PointsTransaction earn = earnTransaction(11L, 1L, 500);
        LoyaltyAccount account = account(1L, 1000);

        when(transactionRepository.findByTypeAndExpiresAtLessThanEqual(eq(PointsTransactionType.EARN), any(LocalDateTime.class)))
                .thenReturn(List.of(earn));
        when(transactionRepository.existsByTypeAndBizTypeAndBizId(PointsTransactionType.EXPIRE, "POINTS_EXPIRE", "11"))
                .thenReturn(false);
        when(accountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

        LoyaltyPoint bucket = new LoyaltyPoint();
        bucket.setUserId(1L);
        bucket.setPoints(500);
        bucket.setAvailablePoints(500);
        bucket.setExpireDate(LocalDate.now().minusDays(1));
        when(loyaltyPointRepository.findByExpireDateLessThanEqualAndAvailablePointsGreaterThan(any(), eq(0)))
                .thenReturn(List.of(bucket));

        pointsExpireService.expire();

        assertEquals(0, bucket.getAvailablePoints());
        assertEquals(500, account.getAvailablePoints());
        assertEquals(500, account.getExpiredPoints());
        assertEquals(500, account.getTotalPoints());
        verify(accountRepository).save(account);

        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        PointsTransaction expire = txCaptor.getValue();
        assertEquals(PointsTransactionType.EXPIRE, expire.getType());
        assertEquals(-500, expire.getAmount());
        assertEquals(500, expire.getBalance());
        assertEquals("POINTS_EXPIRE", expire.getBizType());
        assertEquals("11", expire.getBizId());
    }

    @Test
    void testExpire_skipsAlreadyProcessedEarn() {
        PointsTransaction earn = earnTransaction(11L, 1L, 500);
        when(transactionRepository.findByTypeAndExpiresAtLessThanEqual(eq(PointsTransactionType.EARN), any(LocalDateTime.class)))
                .thenReturn(List.of(earn));
        when(transactionRepository.existsByTypeAndBizTypeAndBizId(PointsTransactionType.EXPIRE, "POINTS_EXPIRE", "11"))
                .thenReturn(true);

        pointsExpireService.expire();

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any(PointsTransaction.class));
    }

    @Test
    void testExpireForUser_onlyLoadsUserExpiredEarns() {
        PointsTransaction earn = earnTransaction(12L, 2L, 300);
        LoyaltyAccount account = account(2L, 800);

        when(transactionRepository.findByUserIdAndTypeAndExpiresAtLessThanEqual(
                eq(2L), eq(PointsTransactionType.EARN), any(LocalDateTime.class)))
                .thenReturn(List.of(earn));
        when(transactionRepository.existsByTypeAndBizTypeAndBizId(PointsTransactionType.EXPIRE, "POINTS_EXPIRE", "12"))
                .thenReturn(false);
        when(accountRepository.findByUserId(2L)).thenReturn(Optional.of(account));

        LoyaltyPoint bucket = new LoyaltyPoint();
        bucket.setUserId(2L);
        bucket.setPoints(300);
        bucket.setAvailablePoints(300);
        bucket.setExpireDate(LocalDate.now().minusDays(1));
        when(loyaltyPointRepository.findByUserIdAndExpireDateLessThanEqualAndAvailablePointsGreaterThan(eq(2L), any(), eq(0)))
                .thenReturn(List.of(bucket));

        pointsExpireService.expireForUser(2L);

        assertEquals(0, bucket.getAvailablePoints());
        assertEquals(500, account.getAvailablePoints());
        assertEquals(300, account.getExpiredPoints());
        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertEquals(-300, txCaptor.getValue().getAmount());
        assertEquals("12", txCaptor.getValue().getBizId());
    }

    private PointsTransaction earnTransaction(Long id, Long userId, int amount) {
        PointsTransaction tx = new PointsTransaction();
        tx.setId(id);
        tx.setUserId(userId);
        tx.setType(PointsTransactionType.EARN);
        tx.setAmount(amount);
        tx.setExpiresAt(LocalDateTime.now().minusDays(1));
        return tx;
    }

    private LoyaltyAccount account(Long userId, int availablePoints) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setMemberLevel(MemberLevel.NORMAL);
        account.setTotalPoints(availablePoints);
        account.setAvailablePoints(availablePoints);
        account.setFrozenPoints(0);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        return account;
    }
}
