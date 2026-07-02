package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.ReviewApprovedEvent;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.loyalty.config.LoyaltyProperties;
import com.ecommerce.loyalty.service.LoyaltyPointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ReviewApprovedEventListener}.
 */
@ExtendWith(MockitoExtension.class)
class ReviewApprovedEventListenerTest {

    @Mock
    private LoyaltyPointService loyaltyPointService;

    @Mock
    private FailedEventRecordRepository failedEventRecordRepository;

    private ReviewApprovedEventListener listener;
    private LoyaltyProperties loyaltyProperties;

    @BeforeEach
    void setUp() {
        RuntimeConfigRegistry.clear();
        loyaltyProperties = new LoyaltyProperties();
        listener = new ReviewApprovedEventListener(loyaltyPointService, failedEventRecordRepository, loyaltyProperties);
    }

    @AfterEach
    void tearDown() {
        RuntimeConfigRegistry.clear();
    }

    /**
     * Verifies that when a review is approved, the listener awards exactly
     * 20 points via {@link LoyaltyPointService#earnPoints}.
     */
    @Test
    void testReviewApproved_awards20Points() {
        Long reviewId = 999L;
        Long userId = 888L;

        ReviewApprovedEvent event = new ReviewApprovedEvent(new Object(), reviewId, userId, 777L, 666L);

        listener.onReviewApproved(event);

        // Verify 20 review reward points are awarded
        verify(loyaltyPointService).earnPoints(
                eq(userId),
                eq(20),
                eq("REVIEW"),
                eq(reviewId.toString()),
                eq("Review reward, reviewId=" + reviewId));
    }

    @Test
    void testReviewRewardPoints_usesConfiguredValue() {
        loyaltyProperties.setReviewRewardPoints(30);
        listener = new ReviewApprovedEventListener(loyaltyPointService, failedEventRecordRepository, loyaltyProperties);
        Long reviewId = 999L;
        Long userId = 888L;

        ReviewApprovedEvent event = new ReviewApprovedEvent(new Object(), reviewId, userId, 777L, 666L);
        listener.onReviewApproved(event);

        verify(loyaltyPointService).earnPoints(
                eq(userId),
                eq(30),
                eq("REVIEW"),
                eq(reviewId.toString()),
                eq("Review reward, reviewId=" + reviewId));
    }

    @Test
    void testReviewApproved_failurePersistsFailedEventRecord() {
        Long reviewId = 999L;
        Long userId = 888L;
        ReviewApprovedEvent event = new ReviewApprovedEvent(new Object(), reviewId, userId, 777L, 666L);
        doThrow(new RuntimeException("review award failed"))
                .when(loyaltyPointService)
                .earnPoints(eq(userId), eq(20), eq("REVIEW"), eq(reviewId.toString()),
                        eq("Review reward, reviewId=" + reviewId));

        listener.onReviewApproved(event);

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        assertEquals("ReviewApprovedEvent", captor.getValue().getEventType());
        assertEquals("review award failed", captor.getValue().getErrorMessage());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("eventType"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("aggregateId"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("traceId"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("orderId"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getEventPayload().contains("productId"));
    }
}
