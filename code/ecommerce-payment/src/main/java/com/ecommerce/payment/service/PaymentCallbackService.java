package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.payment.config.PaymentConfig;
import com.ecommerce.payment.dto.PaymentCallbackRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Handles payment gateway callback processing.
 */
@Service
public class PaymentCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderPaymentStatusUpdater orderPaymentStatusUpdater;
    private final PaymentService paymentService;
    private final PaymentConfig paymentConfig;

    public PaymentCallbackService(PaymentRecordRepository paymentRecordRepository,
                                  OrderPaymentStatusUpdater orderPaymentStatusUpdater,
                                  PaymentService paymentService,
                                  PaymentConfig paymentConfig) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.orderPaymentStatusUpdater = orderPaymentStatusUpdater;
        this.paymentService = paymentService;
        this.paymentConfig = paymentConfig;
    }

    /**
     * Processes a payment callback from the payment gateway.
     */
    @Transactional
    public void processCallback(PaymentCallbackRequest request, String headerSignature) {
        verifySignature(headerSignature);
        request.setSignature(headerSignature);
        log.info("Processing payment callback: paymentNo={}, status={}",
                request.getPaymentNo(), request.getStatus());

        if (request.getCallbackSequence() != null) {
            PaymentRecord existing = paymentRecordRepository
                    .findByPaymentNo(request.getPaymentNo())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "PaymentRecord", request.getPaymentNo()));
            if (request.getCallbackSequence().equals(existing.getCallbackSequence())) {
                log.info("Duplicate callback ignored: paymentNo={}, sequence={}",
                        request.getPaymentNo(), request.getCallbackSequence());
                return;
            }
        }

        String status = request.getStatus() == null ? "SUCCESS" : request.getStatus();
        if ("SUCCESS".equals(status)) {
            processSuccessCallback(request);
        } else if ("FAILED".equals(status)) {
            processFailedCallback(request);
        } else {
            log.warn("Unknown callback status: {}", status);
        }
    }

    public void processCallback(PaymentCallbackRequest request) {
        processCallback(request, request.getSignature());
    }

    private void verifySignature(String headerSignature) {
        String expected = paymentConfig.getCallbackSignature();
        if (headerSignature == null || headerSignature.isBlank()
                || expected == null || expected.isBlank()
                || !expected.equals(headerSignature)) {
            throw new BusinessException("UNAUTHORIZED", "Invalid payment callback signature");
        }
    }

    private void processSuccessCallback(PaymentCallbackRequest request) {
        PaymentRecord payment = paymentRecordRepository
                .findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PaymentRecord", request.getPaymentNo()));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment already SUCCESS: paymentNo={}", request.getPaymentNo());
            return;
        }

        BigDecimal callbackAmount = request.getAmount();
        BigDecimal expectedAmount = payment.getOrderAmount() != null
                ? payment.getOrderAmount() : payment.getPaidAmount();
        if (callbackAmount == null || expectedAmount == null
                || callbackAmount.compareTo(expectedAmount) != 0) {
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH",
                    "Payment callback amount must equal payment payable amount");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAmount(expectedAmount);
        payment.setPaidAt(LocalDateTime.now());
        payment.setCallbackSequence(request.getCallbackSequence());
        payment.setCallbackData("Callback processed at " + LocalDateTime.now());
        paymentRecordRepository.save(payment);

        orderPaymentStatusUpdater.markAsPaid(payment.getOrderId(), payment.getPaymentNo());
        paymentService.confirmPayment(payment);

        log.info("Payment callback processed successfully: paymentNo={}", request.getPaymentNo());
    }

    private void processFailedCallback(PaymentCallbackRequest request) {
        PaymentRecord payment = paymentRecordRepository
                .findByPaymentNo(request.getPaymentNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PaymentRecord", request.getPaymentNo()));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BusinessException("CONFLICT",
                    "Cannot mark as FAILED when already SUCCESS");
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setCallbackSequence(request.getCallbackSequence());
        payment.setCallbackData("Failed callback at " + LocalDateTime.now());
        paymentRecordRepository.save(payment);

        orderPaymentStatusUpdater.markPaymentFailed(payment.getOrderId());

        log.info("Payment callback failed: paymentNo={}", request.getPaymentNo());
    }
}
