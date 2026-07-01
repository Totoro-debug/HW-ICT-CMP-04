package com.ecommerce.review.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewApprovedEventListenerTest {

    @Test
    void configuredRewardPoints_areStoredOnLegacyListener() {
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener(null, 30);

        assertEquals(30, listener.getReviewRewardPoints());
    }

    @Test
    void defaultRewardPoints_remain20OnLegacyListener() {
        ReviewApprovedEventListener listener = new ReviewApprovedEventListener();

        assertEquals(20, listener.getReviewRewardPoints());
    }
}
