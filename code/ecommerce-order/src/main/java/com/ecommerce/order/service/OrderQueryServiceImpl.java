package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.VerifyPurchaseResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of {@link OrderQueryService} and {@link OrderPaymentStatusUpdater}.
 * This is the cross-module interface that other modules (payment, review, logistics, etc.)
 * use to query order data without accessing order repositories directly.
 *
 * <p>Per the architecture specification, the payment module MUST query orders
 * through this service and MUST NOT access order tables directly.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryServiceImpl implements OrderQueryService, OrderPaymentStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductQueryService productQueryService;
    private final OrderPaymentEventHandler paymentEventHandler;

    public OrderQueryServiceImpl(OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 ProductQueryService productQueryService,
                                 OrderPaymentEventHandler paymentEventHandler) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productQueryService = productQueryService;
        this.paymentEventHandler = paymentEventHandler;
    }

    @Override
    public OrderDto getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return toDto(order);
    }

    @Override
    public OrderDto getPayableOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PAYING) {
            throw new BusinessException("ORDER_STATUS_CONFLICT",
                    "Order " + orderId + " is in status " + order.getStatus()
                            + " and cannot be paid");
        }
        return toDto(order);
    }

    @Override
    public VerifyPurchaseResponse verifyPurchase(Long userId, Long productId) {
        Page<Order> orders = orderRepository.findByUserId(userId,
                PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")));

        for (Order order : orders) {
            if (order.getStatus() != OrderStatus.DELIVERED
                    && order.getStatus() != OrderStatus.COMPLETED) {
                continue;
            }
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : items) {
                try {
                    SkuDto sku = productQueryService.getSku(item.getSkuId());
                    if (sku != null && sku.getSpuId().equals(productId)) {
                        return new VerifyPurchaseResponse(true, order.getId(),
                                order.getUpdatedAt());
                    }
                } catch (Exception e) {
                    log.debug("Skipping skuId={} during purchase verification: {}",
                            item.getSkuId(), e.getMessage());
                }
            }
        }
        return new VerifyPurchaseResponse(false, null, null);
    }

    @Override
    public BigDecimal getOrderAmount(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return order.getPayableAmount();
    }

    @Override
    public BigDecimal getAnnualConsumption(Long userId) {
        LocalDateTime startOfYear = LocalDate.now().withDayOfYear(1).atStartOfDay();
        BigDecimal total = orderRepository.sumPayableAmountByUserIdAndStatusInAndPaidAtAfter(
                userId,
                List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED),
                startOfYear);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public void markAsPaid(Long orderId, String paymentNo) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        paymentEventHandler.handlePaymentSuccess(orderId, paymentNo, order.getPayableAmount());
    }

    @Override
    @Transactional
    public void markPaymentFailed(Long orderId) {
        paymentEventHandler.handlePaymentFailure(orderId);
    }

    private OrderDto toDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setExternalOrderNo(order.getExternalOrderNo());
        dto.setStatus(order.getStatus() != null ? order.getStatus().name() : null);
        dto.setItemTotal(order.getItemTotal());
        dto.setShippingFee(order.getShippingFee());
        dto.setPackagingFee(order.getPackagingFee());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setPointsDeductionAmount(order.getPointsDeductionAmount());
        dto.setPayableAmount(order.getPayableAmount());
        dto.setPaidAmount(order.getPaidAmount());
        dto.setAddressSnapshot(order.getAddressSnapshot());
        dto.setCouponIds(order.getCouponIds());
        dto.setRedeemedPoints(order.getRedeemedPoints());
        dto.setPaymentNo(order.getPaymentNo());
        dto.setCancelReason(order.getCancelReason());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setPaidAt(order.getPaidAt());
        dto.setCancelledAt(order.getCancelledAt());
        dto.setExpiresAt(order.getExpiresAt());
        return dto;
    }
}
