package com.ecommerce.review.event;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.ReviewApprovedEvent;
import com.ecommerce.review.service.ReviewApprovedEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewApprovedEventListener}.
 *
 * <p>This legacy listener is kept outside Spring registration; the active
 * reward path is handled by the loyalty module. Tests cover the fallback
 * configured reward value and failure persistence behavior.
 */
@DisplayName("ReviewApprovedEventListener")
class ReviewApprovedEventListenerTest {

    @Test
    @DisplayName("onReviewApproved: awards configured review reward points")
    void testOnReviewApproved_awardsConfiguredPoints() {
        AtomicInteger observedPoints = new AtomicInteger();
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener(null, 30) {
            @Override
            protected void awardReviewPoints(Long userId, Long reviewId) {
                observedPoints.set(getReviewRewardPoints());
            }
        };
        ReviewApprovedEvent event = new ReviewApprovedEvent(this, 1L, 100L);

        assertThatCode(() -> listener.onReviewApproved(event))
                .doesNotThrowAnyException();
        assertThat(observedPoints.get()).isEqualTo(30);
    }

    @Test
    @DisplayName("onReviewApproved: handles multiple events without error")
    void testOnReviewApproved_multipleEvents_noError() {
        // Submit multiple approval events for the same review.
        // (from ReviewService.createReview) and once on approval (from
        // ReviewModerationService.approve). Each invocation awards 20 points.
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener();

        ReviewApprovedEvent submissionEvent = new ReviewApprovedEvent(this, 1L, 100L);
        ReviewApprovedEvent approvalEvent = new ReviewApprovedEvent(this, 1L, 100L);

        // Both invocations should complete without error.
        assertThatCode(() -> listener.onReviewApproved(submissionEvent))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onReviewApproved(approvalEvent))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onReviewApproved: event carries correct reviewId and userId")
    void testEvent_carriesCorrectData() {
        ReviewApprovedEvent event = new ReviewApprovedEvent(this, 42L, 7L);

        assertThat(event.getReviewId()).isEqualTo(42L);
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("onReviewApproved: persists failed event record and does not propagate failure")
    void testOnReviewApproved_failure_persistsFailedEventRecord() {
        FailedEventRecordRepository failedEventRecordRepository = mock(FailedEventRecordRepository.class);
        when(failedEventRecordRepository.save(any(FailedEventRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener(failedEventRecordRepository) {
            @Override
            protected void awardReviewPoints(Long userId, Long reviewId) {
                throw new RuntimeException("loyalty unavailable");
            }
        };
        ReviewApprovedEvent event = new ReviewApprovedEvent(this, 42L, 7L);

        assertThatCode(() -> listener.onReviewApproved(event))
                .doesNotThrowAnyException();

        ArgumentCaptor<FailedEventRecord> captor = ArgumentCaptor.forClass(FailedEventRecord.class);
        verify(failedEventRecordRepository).save(captor.capture());
        FailedEventRecord record = captor.getValue();
        assertThat(record.getEventType()).isEqualTo("ReviewApprovedEvent");
        assertThat(record.getEventPayload()).contains("\"reviewId\":42", "\"userId\":7");
        assertThat(record.getErrorMessage()).isEqualTo("loyalty unavailable");
        assertThat(record.getOccurredAt()).isNotNull();
        assertThat(record.isRetried()).isFalse();
        assertThat(record.getRetryCount()).isZero();
    }
}
