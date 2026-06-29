package com.ecommerce.payment.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.payment.dto.SettlementBatchResponse;
import com.ecommerce.payment.entity.InvoiceRecord;
import com.ecommerce.payment.entity.InvoiceStatus;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.entity.SettlementBatch;
import com.ecommerce.payment.entity.SettlementOrderItem;
import com.ecommerce.payment.entity.SettlementStatus;
import com.ecommerce.payment.repository.InvoiceRecordRepository;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import com.ecommerce.payment.repository.SettlementBatchRepository;
import com.ecommerce.payment.repository.SettlementOrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates daily settlement batches for sales reconciliation.
 */
@Service
public class SettlementBatchService {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchService.class);

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementOrderItemRepository settlementOrderItemRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RefundRecordRepository refundRecordRepository;
    private final InvoiceRecordRepository invoiceRecordRepository;
    private final AuditLogService auditLogService;

    public SettlementBatchService(SettlementBatchRepository settlementBatchRepository,
                                  SettlementOrderItemRepository settlementOrderItemRepository,
                                  PaymentRecordRepository paymentRecordRepository,
                                  RefundRecordRepository refundRecordRepository,
                                  InvoiceRecordRepository invoiceRecordRepository,
                                  AuditLogService auditLogService) {
        this.settlementBatchRepository = settlementBatchRepository;
        this.settlementOrderItemRepository = settlementOrderItemRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.refundRecordRepository = refundRecordRepository;
        this.invoiceRecordRepository = invoiceRecordRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Generates a settlement batch for the given date.
     */
    @Transactional
    public SettlementBatchResponse generateBatch(LocalDate batchDate) {
        log.info("Generating settlement batch for date: {}", batchDate);

        settlementBatchRepository.findByBatchDate(batchDate).ifPresent(existing -> {
            throw new ConflictException("Settlement batch already exists for date: " + batchDate);
        });

        LocalDateTime startOfDay = batchDate.atStartOfDay();
        LocalDateTime endOfDay = batchDate.atTime(LocalTime.MAX);

        List<PaymentRecord> payments = paymentRecordRepository
                .findByStatusAndPaidAtBetweenAndSettledAtIsNull(
                        PaymentStatus.SUCCESS, startOfDay, endOfDay);
        List<RefundRecord> refunds = refundRecordRepository
                .findByStatusAndCompletedAtBetweenAndSettledAtIsNull(
                        RefundStatus.COMPLETED, startOfDay, endOfDay);

        BigDecimal totalPaymentAmount = payments.stream()
                .map(PaymentRecord::getPaidAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, MonetaryUtil::add);

        List<InvoiceRecord> invoices = invoiceRecordRepository.findAll().stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.ISSUED)
                .filter(inv -> inv.getIssuedAt() != null
                        && !inv.getIssuedAt().isBefore(startOfDay)
                        && inv.getIssuedAt().isBefore(endOfDay))
                .collect(Collectors.toList());

        BigDecimal totalInvoiceAmount = invoices.stream()
                .map(InvoiceRecord::getInvoiceAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, MonetaryUtil::add);

        BigDecimal totalRefundAmount = refunds.stream()
                .map(RefundRecord::getRefundAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, MonetaryUtil::add);

        int orderCount = (int) payments.stream()
                .map(PaymentRecord::getOrderId)
                .distinct()
                .count();

        SettlementBatch batch = createBatchEntity(batchDate, totalPaymentAmount,
                totalRefundAmount, totalInvoiceAmount, orderCount);
        batch = settlementBatchRepository.save(batch);

        for (PaymentRecord payment : payments) {
            SettlementOrderItem item = new SettlementOrderItem();
            item.setBatchId(batch.getId());
            item.setOrderId(payment.getOrderId());
            item.setPaymentNo(payment.getPaymentNo());
            item.setPaidAmount(MonetaryUtil.roundToCent(payment.getPaidAmount()));

            invoices.stream()
                    .filter(inv -> inv.getOrderId().equals(payment.getOrderId()))
                    .findFirst()
                    .ifPresent(inv -> {
                        item.setInvoiceId(inv.getId());
                        item.setInvoiceAmount(MonetaryUtil.roundToCent(inv.getInvoiceAmount()));
                    });

            settlementOrderItemRepository.save(item);
            payment.setSettlementBatchNo(batch.getBatchNo());
            payment.setSettledAt(LocalDateTime.now());
            paymentRecordRepository.save(payment);
        }

        for (RefundRecord refund : refunds) {
            refund.setSettlementBatchNo(batch.getBatchNo());
            refund.setSettledAt(LocalDateTime.now());
            refundRecordRepository.save(refund);
        }

        recordBatchAudit(batch, "Settlement batch generated for " + batchDate);
        log.info("Settlement batch generated: batchNo={}, orderCount={}, totalPayment={}",
                batch.getBatchNo(), orderCount, totalPaymentAmount);

        return toBatchResponse(batch);
    }

    private SettlementBatch createBatchEntity(LocalDate batchDate, BigDecimal totalPayment,
                                              BigDecimal totalRefund, BigDecimal totalInvoice,
                                              int orderCount) {
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchNo("BAT" + batchDate.toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        batch.setBatchDate(batchDate);
        batch.setTotalPaymentAmount(MonetaryUtil.roundToCent(totalPayment));
        batch.setTotalRefundAmount(MonetaryUtil.roundToCent(totalRefund));
        batch.setTotalInvoiceAmount(MonetaryUtil.roundToCent(totalInvoice));
        batch.setOrderCount(orderCount);
        batch.setStatus(SettlementStatus.GENERATED);
        batch.setGeneratedAt(LocalDateTime.now());
        return batch;
    }

    private void recordBatchAudit(SettlementBatch batch, String remark) {
        auditLogService.record("SYSTEM", "SYSTEM", "SETTLEMENT_BATCH_GENERATED",
                "SETTLEMENT_BATCH", batch.getBatchNo(), null, batch.getStatus().name(), remark);
    }

    private SettlementBatchResponse toBatchResponse(SettlementBatch batch) {
        SettlementBatchResponse response = new SettlementBatchResponse();
        response.setId(batch.getId());
        response.setBatchNo(batch.getBatchNo());
        response.setBatchDate(batch.getBatchDate());
        response.setTotalPaymentAmount(batch.getTotalPaymentAmount());
        response.setTotalRefundAmount(batch.getTotalRefundAmount());
        response.setTotalInvoiceAmount(batch.getTotalInvoiceAmount());
        response.setOrderCount(batch.getOrderCount());
        response.setStatus(batch.getStatus());
        response.setGeneratedAt(batch.getGeneratedAt());
        response.setCreatedAt(batch.getCreatedAt());
        return response;
    }
}
