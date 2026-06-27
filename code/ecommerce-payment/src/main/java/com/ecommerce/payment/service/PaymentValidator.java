package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.OrderValidationException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.money.MoneyValidationUtil;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates payment requests against business rules.
 */
@Component
public class PaymentValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymentValidator.class);

    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentValidator(PaymentRecordRepository paymentRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
    }

    /**
     * Validates a payment request.
     */
    public void validate(PayRequest request, OrderDto order) {
        if (order == null) {
            throw new BusinessException("RESOURCE_NOT_FOUND",
                    "Order not found: " + request.getOrderId());
        }

        String status = order.getStatus();
        if (!"CREATED".equals(status) && !"PAYING".equals(status)) {
            throw new ConflictException(
                    "Order " + request.getOrderId() + " is not in a payable status: " + status);
        }

        try {
            MoneyValidationUtil.validatePayableAmount(request.getAmount());
        } catch (OrderValidationException ex) {
            throw new OrderValidationException(ex.getCode(), "Payment amount must be at least 0.01");
        }

        BigDecimal payableAmount = order.getPayableAmount();
        if (payableAmount == null || request.getAmount().compareTo(payableAmount) != 0) {
            throw new OrderValidationException("PAYMENT_AMOUNT_MISMATCH",
                    "Payment amount must equal order payable amount");
        }

        if (request.getMethod() == null) {
            throw new ValidationException("method", "Payment method is required");
        }
        try {
            PaymentMethod.valueOf(request.getMethod().name());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("method",
                    "Unsupported payment method: " + request.getMethod());
        }

        if (paymentRecordRepository.existsByOrderIdAndStatus(
                request.getOrderId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException(
                    "Order " + request.getOrderId() + " already has a successful payment");
        }

        log.info("Payment validation passed for orderId={}, amount={}",
                request.getOrderId(), request.getAmount());
    }
}
