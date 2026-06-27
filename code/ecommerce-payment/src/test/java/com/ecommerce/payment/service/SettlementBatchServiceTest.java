package com.ecommerce.payment.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.payment.dto.SettlementBatchResponse;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.SettlementBatch;
import com.ecommerce.payment.entity.SettlementStatus;
import com.ecommerce.payment.repository.InvoiceRecordRepository;
import com.ecommerce.payment.repository.PaymentRecordRepository;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
        PaymentRecord pending = createPayment(3L, "PAY003", new BigDecimal("50.00"), PaymentStatus.PENDING);
        PaymentRecord failed = createPayment(4L, "PAY004", new BigDecimal("75.00"), PaymentStatus.FAILED);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(Arrays.asList(paid1, paid2, pending, failed));
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        SettlementBatch savedBatch = new SettlementBatch();
        savedBatch.setId(1L);
        savedBatch.setBatchNo("BAT20260601ABC");
        savedBatch.setBatchDate(batchDate);
        savedBatch.setTotalPaymentAmount(new BigDecimal("425.01"));
        savedBatch.setTotalRefundAmount(new BigDecimal("0.00"));
        savedBatch.setTotalInvoiceAmount(new BigDecimal("0.00"));
        savedBatch.setOrderCount(4);
        savedBatch.setStatus(SettlementStatus.GENERATED);

        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenReturn(savedBatch);
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        assertNotNull(response);
        assertEquals(4, response.getOrderCount());
        assertEquals(new BigDecimal("425.01"), response.getTotalPaymentAmount());
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
        PaymentRecord pending = createPayment(30L, "PAY030", new BigDecimal("100.00"), PaymentStatus.PENDING);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(Arrays.asList(payment1, payment2, pending));
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        ArgumentCaptor<SettlementBatch> batchCaptor =
                ArgumentCaptor.forClass(SettlementBatch.class);
        when(settlementBatchRepository.save(batchCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        assertEquals(3, response.getOrderCount());
        assertEquals(new BigDecimal("600.01"), response.getTotalPaymentAmount());

        SettlementBatch captured = batchCaptor.getValue();
        assertEquals(batchDate, captured.getBatchDate());
        assertEquals(SettlementStatus.GENERATED, captured.getStatus());
        assertNotNull(captured.getBatchNo());
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
}
