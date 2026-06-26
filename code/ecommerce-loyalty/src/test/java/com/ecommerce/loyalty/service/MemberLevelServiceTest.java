package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.LoyaltyAccount;
import com.ecommerce.loyalty.entity.MemberLevel;
import com.ecommerce.loyalty.query.AnnualConsumptionQueryService;
import com.ecommerce.loyalty.repository.LoyaltyAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MemberLevelService}.
 */
@ExtendWith(MockitoExtension.class)
class MemberLevelServiceTest {

    @Mock
    private LoyaltyAccountRepository accountRepository;

    @Mock
    private ObjectProvider<AnnualConsumptionQueryService> annualConsumptionQueryServiceProvider;

    @Mock
    private AnnualConsumptionQueryService annualConsumptionQueryService;

    private MemberLevelService service;

    @BeforeEach
    void setUp() {
        when(annualConsumptionQueryServiceProvider.getIfAvailable()).thenReturn(annualConsumptionQueryService);
        service = new MemberLevelService(accountRepository, annualConsumptionQueryServiceProvider);
    }

    @Test
    void testEvaluateAndUpgrade_platinumThreshold() {
        Long userId = 1L;
        LoyaltyAccount account = createAccount(userId, MemberLevel.SILVER);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(annualConsumptionQueryService.getAnnualConsumption(userId)).thenReturn(new BigDecimal("25000"));

        MemberLevel result = service.evaluateAndUpgrade(userId);

        assertEquals(MemberLevel.PLATINUM, result,
                "Annual consumption 25000 >= 20000 threshold should result in PLATINUM");

        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(MemberLevel.PLATINUM, captor.getValue().getMemberLevel(),
                "Account member level should be upgraded to PLATINUM");
    }

    @Test
    void testEvaluateAndUpgrade_goldThreshold() {
        Long userId = 2L;
        LoyaltyAccount account = createAccount(userId, MemberLevel.SILVER);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(annualConsumptionQueryService.getAnnualConsumption(userId)).thenReturn(new BigDecimal("6000"));

        MemberLevel result = service.evaluateAndUpgrade(userId);

        assertEquals(MemberLevel.GOLD, result,
                "Annual consumption 6000 >= 5000 threshold should result in GOLD");

        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(MemberLevel.GOLD, captor.getValue().getMemberLevel(),
                "Account member level should be upgraded to GOLD");
    }

    @Test
    void testEvaluateAndUpgrade_silverThreshold() {
        Long userId = 3L;
        LoyaltyAccount account = createAccount(userId, MemberLevel.NORMAL);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(annualConsumptionQueryService.getAnnualConsumption(userId)).thenReturn(new BigDecimal("1500"));

        MemberLevel result = service.evaluateAndUpgrade(userId);

        assertEquals(MemberLevel.SILVER, result,
                "Annual consumption 1500 >= 1000 threshold should result in SILVER");

        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(MemberLevel.SILVER, captor.getValue().getMemberLevel(),
                "Account member level should be upgraded to SILVER");
    }

    @Test
    void testEvaluateAndUpgrade_defaultNormal() {
        Long userId = 4L;
        LoyaltyAccount account = createAccount(userId, MemberLevel.NORMAL);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(annualConsumptionQueryService.getAnnualConsumption(userId)).thenReturn(new BigDecimal("500"));

        MemberLevel result = service.evaluateAndUpgrade(userId);

        assertEquals(MemberLevel.NORMAL, result,
                "Annual consumption 500 < 1000 threshold should remain NORMAL");

        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(MemberLevel.NORMAL, captor.getValue().getMemberLevel(),
                "Account member level should stay NORMAL");
    }

    @Test
    void testLevelCalculation_usesAnnualConsumptionPort() {
        Long userId = 5L;
        LoyaltyAccount account = createAccount(userId, MemberLevel.SILVER);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(annualConsumptionQueryService.getAnnualConsumption(userId)).thenReturn(new BigDecimal("8000"));

        MemberLevel result = service.evaluateAndUpgrade(userId);

        verify(annualConsumptionQueryService).getAnnualConsumption(eq(userId));
        assertEquals(MemberLevel.GOLD, result);
        assertNotNull(annualConsumptionQueryService, "MemberLevelService order data port");
    }

    private LoyaltyAccount createAccount(Long userId, MemberLevel level) {
        LoyaltyAccount account = new LoyaltyAccount();
        account.setUserId(userId);
        account.setMemberLevel(level);
        account.setTotalPoints(0);
        account.setAvailablePoints(0);
        account.setFrozenPoints(0);
        account.setRedeemedPoints(0);
        account.setExpiredPoints(0);
        account.setAnnualConsumption(BigDecimal.ZERO);
        return account;
    }
}
