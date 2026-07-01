package com.ecommerce.payment.service;

import com.ecommerce.common.exception.AuthorizationException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.idempotency.Idempotent;
import com.ecommerce.common.test.RuntimeConfigRegistry;
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
    @Idempotent(businessType = "PAYMENT_CALLBACK", key = "#request.paymentNo + ':' + #request.callbackSequence")
    public String processCallback(PaymentCallbackRequest request, String headerSignature) {
        verifySignature(headerSignature);
        request.setSignature(headerSignature);
        int callbackTimeoutSeconds = RuntimeConfigRegistry.getInt(
                "payment.callback-timeout-seconds", paymentConfig.getCallbackTimeoutSeconds());
        log.info("Processing payment callback: paymentNo={}, status={}, callbackTimeoutSeconds={}",
                request.getPaymentNo(), request.getStatus(), callbackTimeoutSeconds);

        if (request.getCallbackSequence() != null) {
            PaymentRecord existing = paymentRecordRepository
                    .findByPaymentNo(request.getPaymentNo())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "PaymentRecord", request.getPaymentNo()));
            if (request.getCallbackSequence().equals(existing.getCallbackSequence())) {
                log.info("Duplicate callback ignored: paymentNo={}, sequence={}",
                        request.getPaymentNo(), request.getCallbackSequence());
                return "OK";
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
        return "OK";
    }

    public String processCallback(PaymentCallbackRequest request) {
        return processCallback(request, request.getSignature());
    }

    private void verifySignature(String headerSignature) {
        String expected = paymentConfig.getCallbackSignature();
        if (headerSignature == null || headerSignature.isBlank()
                || expected == null || expected.isBlank()
                || !expected.equals(headerSignature)) {
            throw AuthorizationException.forbidden("Invalid payment callback signature");
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
            throw new OrderValidationException("PAYMENT_AMOUNT_MISMATCH",
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
            throw new ConflictException("Cannot mark as FAILED when already SUCCESS");
        }

        int retryTimes = RuntimeConfigRegistry.getInt("payment.retry-times", paymentConfig.getRetryTimes());
        payment.setStatus(PaymentStatus.FAILED);
        payment.setCallbackSequence(request.getCallbackSequence());
        payment.setCallbackData("Failed callback at " + LocalDateTime.now() + ", retryTimes=" + retryTimes);
        paymentRecordRepository.save(payment);

        orderPaymentStatusUpdater.markPaymentFailed(payment.getOrderId());

        log.info("Payment callback failed: paymentNo={}", request.getPaymentNo());
    }
}
