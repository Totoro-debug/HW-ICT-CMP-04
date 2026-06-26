package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.dto.PayResponse;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PaymentService}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private PaymentValidator paymentValidator;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private OrderQueryService orderQueryService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRecordRepository,
                paymentValidator,
                eventPublisher,
                orderQueryService
        );
    }

    @Test
    @DisplayName("pay() should create a PaymentRecord for a valid request")
    void testPay_validRequest_createsPaymentRecord() {
        PayRequest request = new PayRequest(1L, new BigDecimal("99.00"),
                PaymentMethod.ALIPAY, "CLIENT123");

        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setOrderNo("ORD001");
        orderDto.setUserId(100L);
        orderDto.setPayableAmount(new BigDecimal("99.00"));
        orderDto.setStatus("CREATED");

        when(orderQueryService.getPayableOrder(1L)).thenReturn(orderDto);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PayResponse response = paymentService.pay(request);

        assertNotNull(response);
        assertEquals(1L, response.getOrderId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("99.00"), response.getPaidAmount());
        assertNotNull(response.getPaymentNo());

        verify(orderQueryService).getPayableOrder(1L);
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
        verify(paymentValidator).validate(eq(request), eq(orderDto));
    }

    @Test
    @DisplayName("confirmPayment publishes only a domain event")
    void testConfirmPayment_publishesEvent() {
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY001");
        payment.setOrderId(1L);
        payment.setPaidAmount(new BigDecimal("99.00"));
        payment.setStatus(PaymentStatus.PENDING);
        OrderDto order = new OrderDto();
        order.setOrderId(1L);
        order.setUserId(100L);
        when(orderQueryService.getOrder(1L)).thenReturn(order);

        paymentService.confirmPayment(payment);

        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("getPayment() should return PaymentRecord by paymentNo")
    void testGetPayment_returnsPaymentRecord() {
        String paymentNo = "PAY123";
        PaymentRecord record = new PaymentRecord();
        record.setPaymentNo(paymentNo);
        record.setOrderId(1L);
        record.setPaidAmount(new BigDecimal("99.00"));
        record.setStatus(PaymentStatus.SUCCESS);
        record.setCreatedAt(java.time.LocalDateTime.now());

        when(paymentRecordRepository.findByPaymentNo(paymentNo))
                .thenReturn(Optional.of(record));

        PayResponse response = paymentService.getPayment(paymentNo);

        assertNotNull(response);
        assertEquals(paymentNo, response.getPaymentNo());
        assertEquals(1L, response.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("99.00"), response.getPaidAmount());
    }
}
