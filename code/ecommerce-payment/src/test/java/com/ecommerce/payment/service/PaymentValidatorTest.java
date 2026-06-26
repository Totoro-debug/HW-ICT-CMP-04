package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PaymentValidator}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentValidatorTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator(paymentRecordRepository);
    }

    @Test
    @DisplayName("partial payment is rejected with PAYMENT_AMOUNT_MISMATCH")
    void testValidate_partialPayment_rejected() {
        OrderDto order = createOrder(1L, new BigDecimal("100.00"), "CREATED");
        PayRequest request = new PayRequest(1L, new BigDecimal("50.00"),
                PaymentMethod.ALIPAY, "CLIENT1");

        assertThrows(BusinessException.class, () -> validator.validate(request, order));
    }

    @Test
    @DisplayName("over-payment is rejected with PAYMENT_AMOUNT_MISMATCH")
    void testValidate_overPayment_rejected() {
        OrderDto order = createOrder(2L, new BigDecimal("100.00"), "PAYING");
        PayRequest request = new PayRequest(2L, new BigDecimal("999.00"),
                PaymentMethod.BALANCE, "CLIENT2");

        assertThrows(BusinessException.class, () -> validator.validate(request, order));
    }

    @Test
    @DisplayName("zero amount fails validation")
    void testValidate_zeroAmount_fails() {
        OrderDto order = createOrder(3L, new BigDecimal("100.00"), "CREATED");
        PayRequest request = new PayRequest(3L, BigDecimal.ZERO,
                PaymentMethod.ALIPAY, "CLIENT3");

        assertThrows(ValidationException.class, () -> validator.validate(request, order));
    }

    @Test
    @DisplayName("negative amount fails validation")
    void testValidate_negativeAmount_fails() {
        OrderDto order = createOrder(4L, new BigDecimal("100.00"), "CREATED");
        PayRequest request = new PayRequest(4L, new BigDecimal("-10.00"),
                PaymentMethod.ALIPAY, "CLIENT4");

        assertThrows(ValidationException.class, () -> validator.validate(request, order));
    }

    @Test
    @DisplayName("exact amount match passes validation")
    void testValidate_exactMatch_passes() {
        OrderDto order = createOrder(5L, new BigDecimal("99.99"), "CREATED");
        PayRequest request = new PayRequest(5L, new BigDecimal("99.99"),
                PaymentMethod.ALIPAY, "CLIENT5");

        when(paymentRecordRepository.existsByOrderIdAndStatus(eq(5L), eq(PaymentStatus.SUCCESS)))
                .thenReturn(false);

        assertDoesNotThrow(() -> validator.validate(request, order));
    }

    @Test
    @DisplayName("successful duplicate payment is rejected with CONFLICT")
    void testValidate_duplicatePayment_rejected() {
        OrderDto order = createOrder(6L, new BigDecimal("88.00"), "CREATED");
        PayRequest request = new PayRequest(6L, new BigDecimal("88.00"),
                PaymentMethod.WECHAT, "CLIENT6");

        when(paymentRecordRepository.existsByOrderIdAndStatus(eq(6L), eq(PaymentStatus.SUCCESS)))
                .thenReturn(true);

        assertThrows(BusinessException.class, () -> validator.validate(request, order));
    }

    private OrderDto createOrder(Long orderId, BigDecimal payableAmount, String status) {
        OrderDto dto = new OrderDto();
        dto.setOrderId(orderId);
        dto.setOrderNo("ORD" + orderId);
        dto.setUserId(100L);
        dto.setPayableAmount(payableAmount);
        dto.setStatus(status);
        return dto;
    }
}
