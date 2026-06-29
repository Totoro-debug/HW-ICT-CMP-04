package com.ecommerce.payment.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.payment.dto.RefundApplyRequest;
import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.dto.RefundReviewRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private RefundCalculator refundCalculator;

    @Mock
    private RefundStageService refundStageService;

    @Mock
    private AuditLogService auditLogService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                refundRecordRepository,
                paymentRecordRepository,
                refundCalculator,
                refundStageService,
                auditLogService
        );
    }

    @Test
    @DisplayName("review approval moves refund to WAITING_WAREHOUSE_ACCEPT and audits")
    void testReviewRefund_movesToWaitingWarehouseAccept() {
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF001");
        refund.setStatus(RefundStatus.PENDING_REVIEW);
        refund.setReason("Changed mind");

        when(refundRecordRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundReviewRequest reviewRequest = new RefundReviewRequest(true, "Approved by admin");

        RefundResponse response = refundService.reviewRefund(1L, 999L, reviewRequest);

        assertEquals(RefundStatus.WAITING_WAREHOUSE_ACCEPT, response.getStatus());
        assertEquals("Approved by admin", response.getReviewNote());
        verify(auditLogService).record("999", "999", "REFUND_REVIEW", "REFUND", "RF001",
                "PENDING_REVIEW", "WAITING_WAREHOUSE_ACCEPT", "Approved by admin");
    }

    @Test
    @DisplayName("warehouse acceptance returns accepted status and triggers finance stage internally")
    void testWarehouseAccept_returnsAcceptedAndTriggersFinanceStage() {
        RefundRecord accepted = refundRecord(2L, "RF002", RefundStatus.WAREHOUSE_ACCEPTED, new BigDecimal("98.00"));

        when(refundStageService.acceptWarehouse(2L, 999L)).thenReturn(accepted);

        RefundResponse response = refundService.warehouseAccept(2L, 999L);

        assertEquals(RefundStatus.WAREHOUSE_ACCEPTED, response.getStatus());
        verify(refundStageService).acceptWarehouse(2L, 999L);
        verify(refundStageService).executeFinanceRefund(2L);
    }

    @Test
    @DisplayName("warehouse acceptance stays committed when refund execution fails")
    void testWarehouseAccept_keepsAcceptedStatusWhenExecutionFails() {
        RefundRecord accepted = refundRecord(3L, "RF003", RefundStatus.WAREHOUSE_ACCEPTED, new BigDecimal("88.00"));

        when(refundStageService.acceptWarehouse(3L, 1000L)).thenReturn(accepted);
        doThrow(new ConflictException("refund gateway temporarily unavailable"))
                .when(refundStageService).executeFinanceRefund(3L);

        RefundResponse response = refundService.warehouseAccept(3L, 1000L);

        assertEquals(RefundStatus.WAREHOUSE_ACCEPTED, response.getStatus());
    }

    @Test
    @DisplayName("applyRefund creates a refund record in PENDING_REVIEW status")
    void testApplyRefund_createsRefundRecord() {
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY004");
        payment.setOrderId(40L);
        payment.setPaidAmount(new BigDecimal("200.00"));
        payment.setStatus(PaymentStatus.SUCCESS);

        when(refundRecordRepository.findByRefundRequestNo("REQ004"))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaymentNo("PAY004"))
                .thenReturn(Optional.of(payment));
        when(refundCalculator.calculate(new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("195.00"));
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundApplyRequest request = new RefundApplyRequest(40L, "PAY004", "REQ004", "Defective item");

        RefundResponse response = refundService.applyRefund(100L, request);

        assertNotNull(response);
        assertEquals("REQ004", response.getRefundRequestNo());
        assertEquals("PAY004", response.getPaymentNo());
        assertEquals(40L, response.getOrderId());
        assertEquals(100L, response.getUserId());
        assertEquals(RefundStatus.PENDING_REVIEW, response.getStatus());
        assertEquals("Defective item", response.getReason());
        assertNotNull(response.getRefundNo());
        verify(refundCalculator).validateRefundAmount(new BigDecimal("195.00"), new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("applyRefund returns first response for same refundRequestNo")
    void testApplyRefund_duplicateRequest_returnsExistingRefund() {
        RefundRecord existing = refundRecord(4L, "RF004", RefundStatus.PENDING_REVIEW, new BigDecimal("195.00"));
        existing.setRefundRequestNo("REQ004");
        existing.setPaymentNo("PAY004");
        existing.setOrderId(40L);
        existing.setUserId(100L);

        when(refundRecordRepository.findByRefundRequestNo("REQ004"))
                .thenReturn(Optional.of(existing));

        RefundApplyRequest request = new RefundApplyRequest(40L, "PAY004", "REQ004", "Defective item");

        RefundResponse response = refundService.applyRefund(100L, request);

        assertEquals("RF004", response.getRefundNo());
        assertEquals("REQ004", response.getRefundRequestNo());
    }

    @Test
    @DisplayName("applyRefund rejects non-success payment with ConflictException")
    void testApplyRefund_rejectsNonSuccessPayment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY005");
        payment.setOrderId(50L);
        payment.setPaidAmount(new BigDecimal("100.00"));
        payment.setStatus(PaymentStatus.PENDING);

        when(refundRecordRepository.findByRefundRequestNo("REQ005"))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaymentNo("PAY005"))
                .thenReturn(Optional.of(payment));

        RefundApplyRequest request = new RefundApplyRequest(50L, "PAY005", "REQ005", "No longer needed");

        assertThrows(ConflictException.class, () -> refundService.applyRefund(101L, request));
    }

    private RefundRecord refundRecord(Long id, String refundNo, RefundStatus status, BigDecimal amount) {
        RefundRecord refund = new RefundRecord();
        refund.setId(id);
        refund.setRefundNo(refundNo);
        refund.setPaymentNo("PAY" + id);
        refund.setOrderId(id * 10);
        refund.setUserId(id * 100);
        refund.setRefundAmount(amount);
        refund.setStatus(status);
        refund.setReason("reason");
        return refund;
    }
}
