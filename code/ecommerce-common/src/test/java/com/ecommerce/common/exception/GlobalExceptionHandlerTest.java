package com.ecommerce.common.exception;

import com.ecommerce.common.dto.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handles BusinessException with HTTP 400 and includes error code and message")
    void testBusinessException_returns400_withCodeAndMessage() {
        BusinessException ex = new BusinessException("PAYMENT_FAILED", "Insufficient funds");

        ResponseEntity<ApiError> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("handles generic business forbidden-like codes with HTTP 400")
    void testBusinessException_forbiddenCodes_return400() {
        assertBusinessExceptionStatus("USER_NOT_ACTIVE", HttpStatus.BAD_REQUEST);
        assertBusinessExceptionStatus("USER_FROZEN", HttpStatus.BAD_REQUEST);
        assertBusinessExceptionStatus("REVIEW_PURCHASE_REQUIRED", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("handles formerly conflict business error codes with HTTP 400")
    void testBusinessException_conflictCodes_return400() {
        assertBusinessExceptionStatus("ORDER_STATUS_CONFLICT", HttpStatus.BAD_REQUEST);
        assertBusinessExceptionStatus("REFUND_WAITING_WAREHOUSE_ACCEPT", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("handles ResourceNotFoundException with HTTP 404")
    void testResourceNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Product", "SKU-12345");

        ResponseEntity<ApiError> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).contains("SKU-12345");
    }

    @Test
    @DisplayName("handles ValidationException with HTTP 400")
    void testValidationException_returns400() {
        ValidationException ex = new ValidationException("email", "must not be blank");

        ResponseEntity<ApiError> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().getMessage()).contains("email");
    }

    @Test
    @DisplayName("handles ConflictException with HTTP 409")
    void testConflictException_returns409() {
        ConflictException ex = new ConflictException("Order already exists");

        ResponseEntity<ApiError> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("Order already exists");
    }

    @Test
    @DisplayName("handles AuthorizationException with code UNAUTHORIZED and returns HTTP 401")
    void testAuthorizationException_returns401() {
        AuthorizationException ex = AuthorizationException.unauthorized("Invalid token");

        ResponseEntity<ApiError> response = handler.handleAuthorization(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("handles AuthorizationException with USER_FROZEN and returns HTTP 403")
    void testAuthorizationException_userFrozen_returns403() {
        AuthorizationException ex = new AuthorizationException("USER_FROZEN", "Account is frozen");

        ResponseEntity<ApiError> response = handler.handleAuthorization(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("USER_FROZEN");
    }

    @Test
    @DisplayName("handles AuthorizationException with USER_NOT_ACTIVE and returns HTTP 403")
    void testAuthorizationException_userNotActive_returns403() {
        AuthorizationException ex = new AuthorizationException("USER_NOT_ACTIVE", "Account is not active");

        ResponseEntity<ApiError> response = handler.handleAuthorization(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("USER_NOT_ACTIVE");
    }

    @Test
    @DisplayName("handles RateLimitException with HTTP 429")
    void testRateLimitException_returns429() {
        RateLimitException ex = new RateLimitException("Too many requests for key: user123");

        ResponseEntity<ApiError> response = handler.handleRateLimit(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("OrderValidationException extends BusinessException and is handled with HTTP 400")
    void testOrderValidationException_extendsBusinessException() {
        OrderValidationException ex = new OrderValidationException("Amount must be positive");

        assertThat(ex).isInstanceOf(BusinessException.class);

        ResponseEntity<ApiError> response = handler.handleOrderValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("ORDER_INVALID_AMOUNT");
    }

    @Test
    @DisplayName("handles generic Exception with HTTP 500 and includes a traceId")
    void testGenericException_returns500_withTraceId() {
        Exception ex = new NullPointerException("Unexpected null");

        ResponseEntity<ApiError> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getTraceId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("reflection: traceId format is hex-timestamp with 8 hex chars followed by dash and millis")
    void testTraceIdFormat() {
        Exception ex = new RuntimeException("test");

        ResponseEntity<ApiError> response = handler.handleGeneric(ex);

        String traceId = response.getBody().getTraceId();
        assertThat(traceId).matches("[0-9a-f]{8}-\\d{13}");
    }

    @Test
    @DisplayName("handles MethodArgumentNotValidException with HTTP 400 and validation details")
    void testMethodArgumentNotValidException() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "must not be blank"));
        bindingResult.addError(new FieldError("target", "password", "size must be between 8 and 20"));

        java.lang.reflect.Method method = Object.class.getMethod("equals", Object.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(method, 0),
                bindingResult);

        ResponseEntity<ApiError> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().getDetails()).containsKeys("email", "password");
    }

    private void assertBusinessExceptionStatus(String code, HttpStatus expectedStatus) {
        BusinessException ex = new BusinessException(code, "Business rule failed");

        ResponseEntity<ApiError> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(code);
        assertThat(response.getBody().getMessage()).isEqualTo("Business rule failed");
        assertThat(response.getBody().getTraceId()).isNotNull().isNotEmpty();
        assertThat(response.getBody().getDetails()).isEmpty();
    }
}
