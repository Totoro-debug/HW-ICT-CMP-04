package com.ecommerce.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "loyalty")
public class ReviewRewardProperties {

    private int reviewRewardPoints = 20;

    public int getReviewRewardPoints() {
        return reviewRewardPoints;
    }

    public void setReviewRewardPoints(int reviewRewardPoints) {
        this.reviewRewardPoints = reviewRewardPoints;
    }
}
