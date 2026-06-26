package com.ecommerce.payment.service;

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
    @DisplayName("callback with valid signature updates payment and order")
    void testProcessCallback_withValidSignature_succeeds() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY001", 1L, "SUCCESS",
                new BigDecimal("99.00"), "seq-001",
                "valid-signature"
        );

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY001");
        payment.setOrderId(1L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaidAmount(new BigDecimal("99.00"));

        when(paymentRecordRepository.findByPaymentNo("PAY001"))
                .thenReturn(Optional.of(payment));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        callbackService.processCallback(request);

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

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY002");
        payment.setOrderId(2L);
        payment.setPaidAmount(new BigDecimal("199.00"));
        payment.setStatus(PaymentStatus.PENDING);

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
    @DisplayName("duplicate callback with same sequence is handled idempotently")
    void testProcessCallback_duplicateCallback_handledIdempotently() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY003", 3L, "SUCCESS",
                new BigDecimal("299.00"), "seq-003",
                "valid-signature"
        );

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY003");
        payment.setOrderId(3L);
        payment.setPaidAmount(new BigDecimal("299.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCallbackSequence("seq-003");

        when(paymentRecordRepository.findByPaymentNo("PAY003"))
                .thenReturn(Optional.of(payment));

        callbackService.processCallback(request);

        verify(paymentRecordRepository, never()).save(any());
        verify(paymentService, never()).confirmPayment(any());
        verify(orderPaymentStatusUpdater, never()).markAsPaid(any(), any());
    }
}
