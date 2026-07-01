package com.ecommerce.payment.service;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.payment.config.PaymentConfig;
import com.ecommerce.payment.dto.PaymentCallbackRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private OrderPaymentStatusUpdater orderPaymentStatusUpdater;

    @Mock
    private PaymentService paymentService;

    private PaymentCallbackService callbackService;

    @BeforeEach
    void setUp() {
        PaymentConfig paymentConfig = new PaymentConfig();
        paymentConfig.setCallbackSignature("valid-signature");
        callbackService = new PaymentCallbackService(
                paymentRecordRepository,
                orderPaymentStatusUpdater,
                paymentService,
                paymentConfig
        );
    }

    @Test
    @DisplayName("payment config binds appendix B defaults")
    void testPaymentConfig_appendixBDefaults() {
        PaymentConfig paymentConfig = new PaymentConfig();

        assertEquals(5, paymentConfig.getRetryTimes());
        assertEquals(5, paymentConfig.getCallbackTimeoutSeconds());
        assertEquals(new BigDecimal("0.02"), paymentConfig.getRefundFeeRate());
    }

    @Test
    @DisplayName("callback with valid signature updates payment and order")
    void testProcessCallback_withValidSignature_succeeds() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY001", 1L, "SUCCESS",
                new BigDecimal("99.00"), "seq-001",
                "valid-signature"
        );

        PaymentRecord payment = payment("PAY001", 1L, new BigDecimal("99.00"), PaymentStatus.PENDING);

        when(paymentRecordRepository.findByPaymentNo("PAY001"))
                .thenReturn(Optional.of(payment));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String response = callbackService.processCallback(request);

        assertEquals("OK", response);
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
        verify(orderPaymentStatusUpdater).markAsPaid(1L, "PAY001");
        verify(paymentService).confirmPayment(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("successful callback updates payment status to SUCCESS")
    void testProcessCallback_updatesPaymentStatus() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY002", 2L, "SUCCESS",
                new BigDecimal("199.00"), "seq-002",
                "valid-signature"
        );

        PaymentRecord payment = payment("PAY002", 2L, new BigDecimal("199.00"), PaymentStatus.PENDING);

        when(paymentRecordRepository.findByPaymentNo("PAY002"))
                .thenReturn(Optional.of(payment));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        callbackService.processCallback(request);

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordRepository).save(captor.capture());
        PaymentRecord saved = captor.getValue();
        assertEquals(PaymentStatus.SUCCESS, saved.getStatus());
        assertEquals(new BigDecimal("199.00"), saved.getPaidAmount());
        assertEquals("seq-002", saved.getCallbackSequence());
        assertNotNull(saved.getPaidAt());
    }

    @Test
    @DisplayName("duplicate callback with same sequence returns first result without side effects")
    void testProcessCallback_duplicateCallback_handledIdempotently() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY003", 3L, "SUCCESS",
                new BigDecimal("299.00"), "seq-003",
                "valid-signature"
        );

        PaymentRecord payment = payment("PAY003", 3L, new BigDecimal("299.00"), PaymentStatus.SUCCESS);
        payment.setCallbackSequence("seq-003");

        when(paymentRecordRepository.findByPaymentNo("PAY003"))
                .thenReturn(Optional.of(payment));

        String response = callbackService.processCallback(request);

        assertEquals("OK", response);
        verify(paymentRecordRepository, never()).save(any());
        verify(paymentService, never()).confirmPayment(any());
        verify(orderPaymentStatusUpdater, never()).markAsPaid(any(), any());
    }

    @Test
    @DisplayName("invalid signature throws AuthorizationException")
    void testProcessCallback_invalidSignature_throwsAuthorizationException() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY004", 4L, "SUCCESS", new BigDecimal("10.00"), "seq-004", "bad");

        assertThrows(AuthorizationException.class, () -> callbackService.processCallback(request));
    }

    @Test
    @DisplayName("amount mismatch throws OrderValidationException")
    void testProcessCallback_amountMismatch_throwsOrderValidationException() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY005", 5L, "SUCCESS", new BigDecimal("10.01"), "seq-005", "valid-signature");
        PaymentRecord payment = payment("PAY005", 5L, new BigDecimal("10.00"), PaymentStatus.PENDING);

        when(paymentRecordRepository.findByPaymentNo("PAY005"))
                .thenReturn(Optional.of(payment));

        assertThrows(OrderValidationException.class, () -> callbackService.processCallback(request));
    }

    @Test
    @DisplayName("failed callback after success throws ConflictException")
    void testProcessCallback_failedAfterSuccess_throwsConflictException() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY006", 6L, "FAILED", new BigDecimal("10.00"), "seq-006", "valid-signature");
        PaymentRecord payment = payment("PAY006", 6L, new BigDecimal("10.00"), PaymentStatus.SUCCESS);

        when(paymentRecordRepository.findByPaymentNo("PAY006"))
                .thenReturn(Optional.of(payment));

        assertThrows(ConflictException.class, () -> callbackService.processCallback(request));
    }

    private PaymentRecord payment(String paymentNo, Long orderId, BigDecimal amount, PaymentStatus status) {
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(orderId);
        payment.setOrderAmount(amount);
        payment.setPaidAmount(amount);
        payment.setStatus(status);
        return payment;
    }
}
