package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderPaidEventItem;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.dto.PayResponse;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core payment service handling payment creation, querying, and confirmation.
 *
 * <p>Payment confirmation only publishes a local domain event. Downstream
 * logistics, loyalty and notification actions are handled by asynchronous
 * listeners so their failures do not block or roll back the payment flow.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentValidator paymentValidator;
    private final DomainEventPublisher eventPublisher;
    private final OrderQueryService orderQueryService;

    public PaymentService(PaymentRecordRepository paymentRecordRepository,
                          PaymentValidator paymentValidator,
                          DomainEventPublisher eventPublisher,
                          OrderQueryService orderQueryService) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentValidator = paymentValidator;
        this.eventPublisher = eventPublisher;
        this.orderQueryService = orderQueryService;
    }

    /**
     * Initiates a payment for an order.
     */
    @Transactional
    public PayResponse pay(PayRequest request) {
        log.info("Initiating payment for orderId={}, amount={}, method={}",
                request.getOrderId(), request.getAmount(), request.getMethod());

        OrderDto order = orderQueryService.getPayableOrder(request.getOrderId());

        // Validate the payment request
        paymentValidator.validate(request, order);

        // Create payment record
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(request.getOrderId());
        payment.setOrderAmount(order.getPayableAmount());
        payment.setPaidAmount(order.getPayableAmount());
        payment.setMethod(request.getMethod());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setClientPaymentNo(request.getClientPaymentNo());

        payment = paymentRecordRepository.save(payment);

        log.info("Payment record created: paymentNo={}, orderId={}",
                payment.getPaymentNo(), payment.getOrderId());

        return toPayResponse(payment);
    }

    /**
     * Retrieves a payment record by payment number.
     */
    public PayResponse getPayment(String paymentNo) {
        PaymentRecord payment = paymentRecordRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentRecord", paymentNo));
        return toPayResponse(payment);
    }

    /**
     * Confirms a successful payment and triggers downstream actions via event.
     */
    @Transactional
    public void confirmPayment(PaymentRecord payment) {
        log.info("Confirming payment: paymentNo={}, orderId={}",
                payment.getPaymentNo(), payment.getOrderId());

        OrderDto order = orderQueryService.getOrder(payment.getOrderId());
        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : LocalDateTime.now();
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(paidAt);
            paymentRecordRepository.save(payment);
        }
        PaymentSucceededEvent paymentSucceededEvent = new PaymentSucceededEvent(
                this, payment.getPaymentNo(), payment.getOrderId(),
                order.getUserId(), payment.getPaidAmount(), paidAt);
        eventPublisher.publish(paymentSucceededEvent);

        OrderPaidEvent orderPaidEvent = new OrderPaidEvent(
                this, payment.getOrderId(), order.getUserId(),
                payment.getPaymentNo(), payment.getPaidAmount(), toOrderPaidItems(order));
        eventPublisher.publish(orderPaidEvent);

        log.info("Payment confirmed events published: paymentNo={}", payment.getPaymentNo());
    }

    // ---- Utility ----

    private String generatePaymentNo() {
        return "PAY" + System.currentTimeMillis() + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private List<OrderPaidEventItem> toOrderPaidItems(OrderDto order) {
        if (order.getItems() == null) {
            return List.of();
        }
        return order.getItems().stream()
                .map(item -> new OrderPaidEventItem(item.getSkuId(), item.getProductId(), item.getQuantity(),
                        item.getUnitPrice(), item.getPayableAmount()))
                .toList();
    }

    private PayResponse toPayResponse(PaymentRecord payment) {
        PayResponse response = new PayResponse();
        response.setPaymentNo(payment.getPaymentNo());
        response.setOrderId(payment.getOrderId());
        response.setStatus(payment.getStatus());
        response.setPaidAmount(payment.getPaidAmount());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }
}
