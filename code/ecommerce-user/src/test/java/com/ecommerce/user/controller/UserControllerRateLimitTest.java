package com.ecommerce.user.controller;

import com.ecommerce.common.exception.RateLimitException;
import com.ecommerce.common.ratelimit.RateLimit;
import com.ecommerce.common.ratelimit.RateLimitAspect;
import com.ecommerce.user.dto.LoginRequest;
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

@DisplayName("UserController login rate limit")
class UserControllerRateLimitTest {

    @Test
    @DisplayName("limits login by email to five requests per minute")
    void testLoginRateLimit_byEmail_exceedsAfterFiveRequests() throws Throwable {
        Method method = UserController.class.getMethod("login", LoginRequest.class);
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        assertThat(rateLimit).isNotNull();
        assertThat(rateLimit.key()).isEqualTo("#request.email");
        assertThat(rateLimit.permitsPerMinute()).isEqualTo(5);

        LoginRequest request = new LoginRequest();
        request.setEmail("limited@example.com");
        request.setPassword("Password123");

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.proceed()).thenReturn("ok");

        RateLimitAspect aspect = new RateLimitAspect();
        for (int i = 0; i < 5; i++) {
            assertThat(aspect.enforceRateLimit(joinPoint, rateLimit)).isEqualTo("ok");
        }

        assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, rateLimit))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("limited@example.com");
        verify(joinPoint, times(5)).proceed();
    }
}
