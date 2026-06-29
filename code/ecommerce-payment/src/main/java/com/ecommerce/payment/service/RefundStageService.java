package com.ecommerce.payment.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.event.RefundCompletedEvent;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Commits refund acceptance and refund completion in separate transaction stages.
 */
@Service
public class RefundStageService {

    private static final Logger log = LoggerFactory.getLogger(RefundStageService.class);

    private final RefundRecordRepository refundRecordRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public RefundStageService(RefundRecordRepository refundRecordRepository,
                              PaymentRecordRepository paymentRecordRepository,
                              DomainEventPublisher eventPublisher,
                              AuditLogService auditLogService) {
        this.refundRecordRepository = refundRecordRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public RefundRecord acceptWarehouse(Long refundId, Long acceptorId) {
        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));

        RefundStatus beforeStatus = refund.getStatus();
        if (beforeStatus != RefundStatus.WAITING_WAREHOUSE_ACCEPT) {
            throw new ConflictException(
                    "Refund must pass review and wait for warehouse acceptance before completion");
        }

        refund.setStatus(RefundStatus.WAREHOUSE_ACCEPTED);
        refund.setWarehouseAcceptorId(acceptorId);
        RefundRecord saved = refundRecordRepository.save(refund);
        auditLogService.record(String.valueOf(acceptorId), String.valueOf(acceptorId),
                "REFUND_WAREHOUSE_ACCEPT", "REFUND", saved.getRefundNo(),
                beforeStatus.name(), saved.getStatus().name(), "Warehouse accepted returned goods");
        log.info("Refund warehouse acceptance committed: refundNo={}", saved.getRefundNo());
        return saved;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeFinanceRefund(Long refundId) {
        RefundRecord refund = refundRecordRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("RefundRecord", refundId));

        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return;
        }
        if (refund.getStatus() != RefundStatus.WAREHOUSE_ACCEPTED) {
            throw new ConflictException("Refund is not ready for completion: " + refund.getStatus());
        }

        PaymentRecord payment = paymentRecordRepository.findByPaymentNo(refund.getPaymentNo())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentRecord", refund.getPaymentNo()));
        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new ConflictException("Cannot complete refund for payment in status: " + payment.getStatus());
        }

        refund.setStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(LocalDateTime.now());
        RefundRecord savedRefund = refundRecordRepository.save(refund);

        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRecordRepository.save(payment);
        }

        RefundCompletedEvent event = new RefundCompletedEvent(
                this, savedRefund.getRefundNo(), savedRefund.getPaymentNo(),
                savedRefund.getOrderId(), savedRefund.getUserId(), savedRefund.getRefundAmount());
        eventPublisher.publish(event);

        log.info("Refund completed: refundNo={}, amount={}", savedRefund.getRefundNo(), savedRefund.getRefundAmount());
    }
}
