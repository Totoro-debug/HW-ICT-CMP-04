package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
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

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                refundRecordRepository,
                paymentRecordRepository,
                refundCalculator,
                refundStageService
        );
    }

    @Test
    @DisplayName("review approval moves refund to WAITING_WAREHOUSE_ACCEPT")
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
    }

    @Test
    @DisplayName("warehouse acceptance can complete refund in separate stage")
    void testWarehouseAccept_completesRefundWhenExecutionSucceeds() {
        RefundRecord accepted = new RefundRecord();
        accepted.setId(2L);
        accepted.setRefundNo("RF002");
        accepted.setPaymentNo("PAY002");
        accepted.setOrderId(20L);
        accepted.setUserId(200L);
        accepted.setRefundAmount(new BigDecimal("97.00"));
        accepted.setStatus(RefundStatus.WAREHOUSE_ACCEPTED);

        RefundRecord completed = new RefundRecord();
        completed.setId(2L);
        completed.setRefundNo("RF002");
        completed.setPaymentNo("PAY002");
        completed.setOrderId(20L);
        completed.setUserId(200L);
        completed.setRefundAmount(new BigDecimal("97.00"));
        completed.setStatus(RefundStatus.COMPLETED);

        when(refundStageService.acceptWarehouse(2L, 999L)).thenReturn(accepted);
        when(refundStageService.completeRefund(2L)).thenReturn(completed);

        RefundResponse response = refundService.warehouseAccept(2L, 999L);

        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        verify(refundStageService).acceptWarehouse(2L, 999L);
        verify(refundStageService).completeRefund(2L);
    }

    @Test
    @DisplayName("warehouse acceptance stays committed when refund execution fails")
    void testWarehouseAccept_keepsAcceptedStatusWhenExecutionFails() {
        RefundRecord accepted = new RefundRecord();
        accepted.setId(3L);
        accepted.setRefundNo("RF003");
        accepted.setPaymentNo("PAY003");
        accepted.setOrderId(30L);
        accepted.setUserId(300L);
        accepted.setRefundAmount(new BigDecimal("88.00"));
        accepted.setStatus(RefundStatus.WAREHOUSE_ACCEPTED);

        when(refundStageService.acceptWarehouse(3L, 1000L)).thenReturn(accepted);
        when(refundStageService.completeRefund(3L))
                .thenThrow(new BusinessException("CONFLICT", "refund gateway temporarily unavailable"));

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

        when(paymentRecordRepository.findByPaymentNo("PAY004"))
                .thenReturn(Optional.of(payment));
        when(refundCalculator.calculate(new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("195.00"));
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundApplyRequest request = new RefundApplyRequest(40L, "PAY004", "Defective item");

        RefundResponse response = refundService.applyRefund(100L, request);

        assertNotNull(response);
        assertEquals("PAY004", response.getPaymentNo());
        assertEquals(40L, response.getOrderId());
        assertEquals(100L, response.getUserId());
        assertEquals(RefundStatus.PENDING_REVIEW, response.getStatus());
        assertEquals("Defective item", response.getReason());
        assertNotNull(response.getRefundNo());
    }

    @Test
    @DisplayName("applyRefund rejects non-success payment with CONFLICT")
    void testApplyRefund_rejectsNonSuccessPayment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY005");
        payment.setOrderId(50L);
        payment.setPaidAmount(new BigDecimal("100.00"));
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRecordRepository.findByPaymentNo("PAY005"))
                .thenReturn(Optional.of(payment));

        RefundApplyRequest request = new RefundApplyRequest(50L, "PAY005", "No longer needed");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refundService.applyRefund(101L, request));
        assertEquals("CONFLICT", ex.getCode());
    }
}
