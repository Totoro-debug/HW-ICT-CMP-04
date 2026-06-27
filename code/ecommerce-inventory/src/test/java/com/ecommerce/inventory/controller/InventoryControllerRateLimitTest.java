package com.ecommerce.inventory.controller;

import com.ecommerce.common.exception.RateLimitException;
import com.ecommerce.common.ratelimit.RateLimit;
import com.ecommerce.common.ratelimit.RateLimitAspect;
import com.ecommerce.inventory.dto.InventoryCheckRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryController rate limit")
class InventoryControllerRateLimitTest {

    @Test
    @DisplayName("limits inventory check by sku to 120 requests per minute")
    void testCheckAvailabilityRateLimit_bySku_exceedsAfter120Requests() throws Throwable {
        Method method = InventoryController.class.getMethod("checkAvailability", InventoryCheckRequest.class);
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        assertThat(rateLimit).isNotNull();
        assertThat(rateLimit.key()).isEqualTo("'inventory:check:' + #request.skuId");
        assertThat(rateLimit.permitsPerMinute()).isEqualTo(120);

        InventoryCheckRequest request = new InventoryCheckRequest();
        request.setSkuId(100L);
        request.setQuantity(1);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.proceed()).thenReturn("ok");

        RateLimitAspect aspect = new RateLimitAspect();
        for (int i = 0; i < 120; i++) {
            assertThat(aspect.enforceRateLimit(joinPoint, rateLimit)).isEqualTo("ok");
        }

        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("inventory:check:100");
        verify(joinPoint, times(120)).proceed();
    }
}
