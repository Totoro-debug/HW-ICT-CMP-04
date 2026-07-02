package com.ecommerce.payment.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.payment.dto.SettlementBatchResponse;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.entity.SettlementBatch;
import com.ecommerce.payment.entity.SettlementStatus;
import com.ecommerce.payment.repository.InvoiceRecordRepository;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import com.ecommerce.payment.repository.SettlementBatchRepository;
import com.ecommerce.payment.repository.SettlementOrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SettlementBatchService}.
 */
@ExtendWith(MockitoExtension.class)
class SettlementBatchServiceTest {

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    @Mock
    private SettlementOrderItemRepository settlementOrderItemRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private InvoiceRecordRepository invoiceRecordRepository;

    @Mock
    private AuditLogService auditLogService;

    private SettlementBatchService settlementBatchService;

    @BeforeEach
    void setUp() {
        settlementBatchService = new SettlementBatchService(
                settlementBatchRepository,
                settlementOrderItemRepository,
                paymentRecordRepository,
                refundRecordRepository,
                invoiceRecordRepository,
                auditLogService
        );
    }

    @Test
    @DisplayName("settlement includes payments returned by repository and writes audit log")
    void testGenerateBatch_includesRepositoryPaymentsAndAudits() {
        LocalDate batchDate = LocalDate.of(2026, 6, 1);

        PaymentRecord paid1 = createPayment(1L, "PAY001", new BigDecimal("100.005"), PaymentStatus.SUCCESS);
        PaymentRecord paid2 = createPayment(2L, "PAY002", new BigDecimal("200.00"), PaymentStatus.SUCCESS);
        PaymentRecord pending = createPayment(3L, "PAY003", new BigDecimal("50.00"), PaymentStatus.CREATED);
        PaymentRecord failed = createPayment(4L, "PAY004", new BigDecimal("75.00"), PaymentStatus.FAILED);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByStatusAndPaidAtBetweenAndSettledAtIsNull(
                eq(PaymentStatus.SUCCESS), any(), any()))
                .thenReturn(Arrays.asList(paid1, paid2));
        when(refundRecordRepository.findByStatusAndCompletedAtBetweenAndSettledAtIsNull(
                eq(RefundStatus.REFUNDED), any(), any()))
                .thenReturn(Collections.emptyList());
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        SettlementBatch savedBatch = new SettlementBatch();
        savedBatch.setId(1L);
        savedBatch.setBatchNo("BAT20260601ABC");
        savedBatch.setBatchDate(batchDate);
        savedBatch.setTotalPaymentAmount(new BigDecimal("300.01"));
        savedBatch.setTotalRefundAmount(new BigDecimal("0.00"));
        savedBatch.setTotalInvoiceAmount(new BigDecimal("0.00"));
        savedBatch.setOrderCount(2);
        savedBatch.setStatus(SettlementStatus.GENERATED);

        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenReturn(savedBatch);
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        assertNotNull(response);
        assertEquals(2, response.getOrderCount());
        assertEquals(new BigDecimal("300.01"), response.getTotalPaymentAmount());
        verify(auditLogService).record("SYSTEM", "SYSTEM", "SETTLEMENT_BATCH_GENERATED",
                "SETTLEMENT_BATCH", "BAT20260601ABC", null, "GENERATED",
                "Settlement batch generated for 2026-06-01");
    }

    @Test
    @DisplayName("settlement batch rounds totals with HALF_UP")
    void testGenerateBatch_calculatesTotals() {
        LocalDate batchDate = LocalDate.of(2026, 6, 2);

        PaymentRecord payment1 = createPayment(10L, "PAY010", new BigDecimal("150.005"), PaymentStatus.SUCCESS);
        PaymentRecord payment2 = createPayment(20L, "PAY020", new BigDecimal("350.00"), PaymentStatus.SUCCESS);
        PaymentRecord pending = createPayment(30L, "PAY030", new BigDecimal("100.00"), PaymentStatus.CREATED);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByStatusAndPaidAtBetweenAndSettledAtIsNull(
                eq(PaymentStatus.SUCCESS), any(), any()))
                .thenReturn(Arrays.asList(payment1, payment2));
        when(refundRecordRepository.findByStatusAndCompletedAtBetweenAndSettledAtIsNull(
                eq(RefundStatus.REFUNDED), any(), any()))
                .thenReturn(Collections.emptyList());
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        ArgumentCaptor<SettlementBatch> batchCaptor =
                ArgumentCaptor.forClass(SettlementBatch.class);
        when(settlementBatchRepository.save(batchCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        assertEquals(2, response.getOrderCount());
        assertEquals(new BigDecimal("500.01"), response.getTotalPaymentAmount());

        SettlementBatch captured = batchCaptor.getValue();
        assertEquals(batchDate, captured.getBatchDate());
        assertEquals(SettlementStatus.GENERATED, captured.getStatus());
        assertNotNull(captured.getBatchNo());
    }

    @Test
    @DisplayName("settlement writes payment marks and completed refund total")
    void testGenerateBatch_marksSettledPaymentsAndTotalsRefunds() {
        LocalDate batchDate = LocalDate.of(2026, 6, 3);
        PaymentRecord payment = createPayment(100L, "PAY100", new BigDecimal("300.00"), PaymentStatus.SUCCESS);
        RefundRecord refund = createRefund("RF100", new BigDecimal("98.00"));
        ArrayList<PaymentRecord> payments = new ArrayList<>(Arrays.asList(payment));
        ArrayList<RefundRecord> refunds = new ArrayList<>(Arrays.asList(refund));

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByStatusAndPaidAtBetweenAndSettledAtIsNull(
                eq(PaymentStatus.SUCCESS), any(), any()))
                .thenReturn(payments);
        when(refundRecordRepository.findByStatusAndCompletedAtBetweenAndSettledAtIsNull(
                eq(RefundStatus.REFUNDED), any(), any()))
                .thenReturn(refunds);
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());
        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenAnswer(invocation -> {
                    SettlementBatch batch = invocation.getArgument(0);
                    batch.setId(9L);
                    return batch;
                });
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        assertEquals(new BigDecimal("98.00"), response.getTotalRefundAmount());
        assertNotNull(payment.getSettledAt());
        assertNotNull(payment.getSettlementBatchNo());
        assertNotNull(refund.getSettledAt());
        assertEquals(payment.getSettlementBatchNo(), refund.getSettlementBatchNo());
    }

    private PaymentRecord createPayment(Long orderId, String paymentNo,
                                         BigDecimal paidAmount, PaymentStatus status) {
        PaymentRecord p = new PaymentRecord();
        p.setPaymentNo(paymentNo);
        p.setOrderId(orderId);
        p.setPaidAmount(paidAmount);
        p.setStatus(status);
        return p;
    }

    private RefundRecord createRefund(String refundNo, BigDecimal refundAmount) {
        RefundRecord refund = new RefundRecord();
        refund.setRefundNo(refundNo);
        refund.setPaymentNo("PAY100");
        refund.setOrderId(100L);
        refund.setUserId(200L);
        refund.setRefundAmount(refundAmount);
        refund.setReason("reason");
        refund.setStatus(RefundStatus.REFUNDED);
        return refund;
    }
}
