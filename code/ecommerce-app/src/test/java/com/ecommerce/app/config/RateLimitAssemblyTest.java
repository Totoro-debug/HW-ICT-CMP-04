package com.ecommerce.app.config;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.common.exception.RateLimitException;
import com.ecommerce.common.ratelimit.RateLimit;
import com.ecommerce.order.controller.OrderController;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.payment.controller.PaymentController;
import com.ecommerce.payment.dto.PaymentCallbackRequest;
import com.ecommerce.product.controller.ProductController;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.user.controller.UserController;
import com.ecommerce.user.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Rate limit assembly")
class RateLimitAssemblyTest {

    @Test
    @DisplayName("login uses username/email rate limit of five requests per minute")
    void login_hasRateLimit() throws Exception {
        Method method = UserController.class.getMethod("login", LoginRequest.class);

        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertNotNull(rateLimit);
        assertEquals("#request.email", rateLimit.key());
        assertEquals(5, rateLimit.permitsPerMinute());
    }

    @Test
    @DisplayName("payment callback uses paymentNo rate limit of twenty requests per minute")
    void paymentCallback_hasRateLimit() throws Exception {
        Method method = PaymentController.class.getMethod("callback", PaymentCallbackRequest.class, String.class);

        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertNotNull(rateLimit);
        assertEquals("#request.paymentNo", rateLimit.key());
        assertEquals(20, rateLimit.permitsPerMinute());
    }

    @Test
    @DisplayName("product search uses client IP rate limit of one hundred twenty requests per minute")
    void productSearch_hasRateLimit() throws Exception {
        Method method = ProductController.class.getMethod("searchProducts", ProductSearchRequest.class, HttpServletRequest.class);

        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertNotNull(rateLimit);
        assertEquals("#httpRequest.remoteAddr", rateLimit.key());
        assertEquals(120, rateLimit.permitsPerMinute());
    }

    @Test
    @DisplayName("create order uses current user rate limit of twenty requests per minute")
    void createOrder_hasRateLimit() throws Exception {
        Method method = OrderController.class.getMethod("createOrder", CreateOrderRequest.class);

        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        assertNotNull(rateLimit);
        assertEquals("#request != null ? T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName() : 'anonymous'",
                rateLimit.key());
        assertEquals(20, rateLimit.permitsPerMinute());
    }

    @Test
    @DisplayName("rate limit exception maps to HTTP 429 with RATE_LIMITED code")
    void rateLimitException_mapsTo429() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleRateLimit(new RateLimitException("too many requests"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RATE_LIMITED", response.getBody().getCode());
    }
}
