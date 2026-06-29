package com.ecommerce.loyalty.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsExpireSchedulerTest {

    @Test
    void testExpireMonthly_delegatesToExpireService() {
        PointsExpireService pointsExpireService = mock(PointsExpireService.class);
        PointsExpireScheduler scheduler = new PointsExpireScheduler(pointsExpireService);

        scheduler.expireMonthly();

        verify(pointsExpireService).expire();
    }
}
