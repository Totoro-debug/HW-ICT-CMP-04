package com.ecommerce.loyalty.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monthly scheduler for expiring loyalty points.
 */
@Component
public class PointsExpireScheduler {

    private final PointsExpireService pointsExpireService;

    public PointsExpireScheduler(PointsExpireService pointsExpireService) {
        this.pointsExpireService = pointsExpireService;
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void expireMonthly() {
        pointsExpireService.expire();
    }
}
