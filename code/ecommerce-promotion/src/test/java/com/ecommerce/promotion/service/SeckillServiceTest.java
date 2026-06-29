package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.entity.SeckillPurchaseRecord;
import com.ecommerce.promotion.repository.SeckillPurchaseRecordRepository;
import com.ecommerce.promotion.repository.SeckillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeckillService")
class SeckillServiceTest {

    @Mock
    private SeckillRepository seckillRepository;

    @Mock
    private SeckillPurchaseRecordRepository seckillPurchaseRecordRepository;

    @InjectMocks
    private SeckillService seckillService;

    @Captor
    private ArgumentCaptor<SeckillActivity> activityCaptor;

    @Captor
    private ArgumentCaptor<SeckillPurchaseRecord> recordCaptor;

    private SeckillActivity activity;

    @BeforeEach
    void setUp() {
        activity = new SeckillActivity();
        activity.setId(1L);
        activity.setName("iPhone Flash Sale");
        activity.setSkuId(100L);
        activity.setSeckillPrice(new BigDecimal("999.00"));
        activity.setStockQuantity(100);
        activity.setSoldQuantity(0);
        activity.setPerUserLimit(2);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("create rounds seckill price and sets defaults")
    void create_roundsPriceAndSetsDefaults() {
        activity.setSeckillPrice(new BigDecimal("999.005"));
        when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

        seckillService.create(activity);

        verify(seckillRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getSeckillPrice()).isEqualByComparingTo(new BigDecimal("999.01"));
        assertThat(activityCaptor.getValue().getSoldQuantity()).isEqualTo(0);
        assertThat(activityCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("create rejects invalid time range")
    void create_rejectsInvalidTimeRange() {
        activity.setStartTime(LocalDateTime.now());
        activity.setEndTime(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> seckillService.create(activity))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    @DisplayName("validateSeckill enforces user limit with requested quantity")
    void validateSeckill_enforcesUserLimitWithQuantity() {
        when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE")).thenReturn(Optional.of(activity));
        when(seckillPurchaseRecordRepository.sumQuantityByActivityIdAndUserId(1L, 99L)).thenReturn(1);

        assertThatThrownBy(() -> seckillService.validateSeckill(100L, 99L, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("limit");
    }

    @Test
    @DisplayName("validateSeckill enforces stock against requested quantity")
    void validateSeckill_enforcesStockAgainstRequestedQuantity() {
        activity.setStockQuantity(5);
        activity.setSoldQuantity(4);
        when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE")).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> seckillService.validateSeckill(100L, 99L, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    @DisplayName("validateSeckill still supports compatibility overload")
    void validateSeckill_compatibilityOverload() {
        when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE")).thenReturn(Optional.of(activity));

        SeckillActivity result = seckillService.validateSeckill(100L);
        assertThat(result).isSameAs(activity);
    }

    @Test
    @DisplayName("recordPurchase records quantity by user and increments sold quantity")
    void recordPurchase_recordsQuantityByUser() {
        when(seckillRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

        seckillService.recordPurchase(1L, 88L, 100L, 2, 777L);

        verify(seckillRepository).save(activityCaptor.capture());
        assertThat(activityCaptor.getValue().getSoldQuantity()).isEqualTo(2);
        verify(seckillPurchaseRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getUserId()).isEqualTo(88L);
        assertThat(recordCaptor.getValue().getQuantity()).isEqualTo(2);
        assertThat(recordCaptor.getValue().getOrderId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("recordPurchase throws when activity not found")
    void recordPurchase_notFound() {
        when(seckillRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seckillService.recordPurchase(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
