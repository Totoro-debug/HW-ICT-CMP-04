package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.RefundApplyRequest;
import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.dto.RefundReviewRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handles the refund lifecycle: application, review, warehouse acceptance, and completion.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRecordRepository refundRecordRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundCalculator refundCalculator;
    private final RefundStageService refundStageService;

    public RefundService(RefundRecordRepository refundRecordRepository,
                         PaymentRecordRepository paymentRecordRepository,
                         RefundCalculator refundCalculator,
                         RefundStageService refundStageService) {
        this.refundRecordRepository = refundRecordRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundCalculator = refundCalculator;
        this.refundStageService = refundStageService;
    }

    /**
     * Applies for a refund.
     */
    @Transactional
    public RefundResponse applyRefund(Long userId, RefundApplyRequest request) {
        log.info("Applying refund: userId={}, orderId={}, paymentNo={}",
                userId, request.getOrderId(), request.getPaymentNo());

        PaymentRecord payment = paymentRecordRepository
                .findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PaymentRecord", request.getPaymentNo()));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException("CONFLICT",
                    "Refund can only be applied for successfully paid orders");
        }

        BigDecimal refundAmount = refundCalculator.calculate(payment.getPaidAmount());

        RefundRecord refund = new RefundRecord();
        refund.setRefundNo(generateRefundNo());
        refund.setPaymentNo(request.getPaymentNo());
        refund.setOrderId(request.getOrderId());
        refund.setUserId(userId);
        refund.setRefundAmount(refundAmount);
        refund.setReason(request.getReason());
        refund.setStatus(RefundStatus.PENDING_REVIEW);

        refund = refundRecordRepository.save(refund);

        log.info("Refund applied: refundNo={}, amount={}", refund.getRefundNo(), refund.getRefundAmount());

        return toRefundResponse(refund);
    }

    /**
     * Admin reviews a refund application.
     */
    @Transactional
    public RefundResponse reviewRefund(Long refundId, Long reviewerId, RefundReviewRequest request) {
        log.info("Reviewing refund: refundId={}, reviewerId={}, approved={}",
                refundId, reviewerId, request.isApproved());

        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));

        if (refund.getStatus() != RefundStatus.PENDING_REVIEW) {
            throw new BusinessException("CONFLICT",
                    "Refund is not in PENDING_REVIEW status: " + refund.getStatus());
        }

        if (request.isApproved()) {
            refund.setStatus(RefundStatus.WAITING_WAREHOUSE_ACCEPT);
            refund.setReviewerId(reviewerId);
            refund.setReviewNote(request.getNote());
            refundRecordRepository.save(refund);

            log.info("Refund approved and waiting for warehouse acceptance: refundNo={}", refund.getRefundNo());
        } else {
            refund.setStatus(RefundStatus.REJECTED);
            refund.setReviewerId(reviewerId);
            refund.setReviewNote(request.getNote());
            refundRecordRepository.save(refund);

            log.info("Refund rejected: refundNo={}", refund.getRefundNo());
        }

        return toRefundResponse(refund);
    }

    /**
     * Warehouse accepts returned goods and then triggers refund execution in a separate transaction.
     */
    public RefundResponse warehouseAccept(Long refundId, Long acceptorId) {
        log.info("Warehouse accepting refund: refundId={}, acceptorId={}", refundId, acceptorId);

        RefundRecord acceptedRefund = refundStageService.acceptWarehouse(refundId, acceptorId);
        try {
            RefundRecord completedRefund = refundStageService.completeRefund(refundId);
            return toRefundResponse(completedRefund);
        } catch (Exception ex) {
            log.error("Refund execution failed after warehouse acceptance, keeping acceptance committed: refundId={}, error={}",
                    refundId, ex.getMessage(), ex);
            return toRefundResponse(acceptedRefund);
        }
    }

    /**
     * Gets a refund by ID.
     */
    public RefundResponse getRefund(Long refundId) {
        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));
        return toRefundResponse(refund);
    }

    private String generateRefundNo() {
        return "RF" + System.currentTimeMillis() + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private RefundResponse toRefundResponse(RefundRecord refund) {
        RefundResponse response = new RefundResponse();
        response.setId(refund.getId());
        response.setRefundNo(refund.getRefundNo());
        response.setPaymentNo(refund.getPaymentNo());
        response.setOrderId(refund.getOrderId());
        response.setUserId(refund.getUserId());
        response.setRefundAmount(refund.getRefundAmount());
        response.setReason(refund.getReason());
        response.setStatus(refund.getStatus());
        response.setReviewNote(refund.getReviewNote());
        response.setCompletedAt(refund.getCompletedAt());
        response.setCreatedAt(refund.getCreatedAt());
        return response;
    }
}
