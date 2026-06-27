package com.ecommerce.review.service;

import com.ecommerce.common.event.FailedEventRecord;
import com.ecommerce.common.event.FailedEventRecordRepository;
import com.ecommerce.common.event.ReviewApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Legacy review-side handler kept out of Spring registration.
 * Review points are handled by ecommerce-loyalty.
 */
public class ReviewApprovedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewApprovedEventListener.class);

    /** Review reward points per approved review (matches loyalty.review-reward-points config). */
    private static final int REVIEW_REWARD_POINTS = 20;

    private final FailedEventRecordRepository failedEventRecordRepository;

    public ReviewApprovedEventListener() {
        this(null);
    }

    public ReviewApprovedEventListener(FailedEventRecordRepository failedEventRecordRepository) {
        this.failedEventRecordRepository = failedEventRecordRepository;
    }

    public void onReviewApproved(ReviewApprovedEvent event) {
        log.info("Received ReviewApprovedEvent: reviewId={}, userId={}",
                event.getReviewId(), event.getUserId());

        try {
            // In production, this would call LoyaltyPointService.earnPoints()
            // to award REVIEW_REWARD_POINTS to the user. For this module, the
            // points award is handled by the event-driven integration.
            awardReviewPoints(event.getUserId(), event.getReviewId());
            log.info("Awarded {} review reward points for reviewId={}, userId={}",
                    REVIEW_REWARD_POINTS, event.getReviewId(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to award review points for reviewId={}: {}",
                    event.getReviewId(), e.getMessage(), e);
            persistFailure(event, e);
        }
    }

    private void persistFailure(ReviewApprovedEvent event, Exception exception) {
        if (failedEventRecordRepository == null) {
            return;
        }
        try {
            FailedEventRecord record = new FailedEventRecord();
            record.setEventType("ReviewApprovedEvent");
            record.setEventPayload("{\"reviewId\":" + event.getReviewId()
                    + ",\"userId\":" + event.getUserId() + "}");
            record.setErrorMessage(exception.getMessage());
            record.setOccurredAt(LocalDateTime.now());
            record.setRetried(false);
            record.setRetryCount(0);
            failedEventRecordRepository.save(record);
        } catch (Exception persistException) {
            log.error("Failed to persist review approved event failure for reviewId={}: {}",
                    event.getReviewId(), persistException.getMessage(), persistException);
        }
    }

    /**
     * Award review reward points.
     * In a real implementation, this would integrate with the loyalty module
     * via {@code LoyaltyPointService.earnPoints()}.
     */
    protected void awardReviewPoints(Long userId, Long reviewId) {
        // In production, this would call:
        //   loyaltyPointService.earnPoints(userId, REVIEW_REWARD_POINTS,
        //       "REVIEW", reviewId.toString(),
        //       "Review reward, reviewId=" + reviewId);
        log.info("Awarding {} review points to userId={} for reviewId={}",
                REVIEW_REWARD_POINTS, userId, reviewId);
    }
}
