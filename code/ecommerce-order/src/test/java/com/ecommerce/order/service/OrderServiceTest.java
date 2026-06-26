package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.integration.LoyaltyCommandService;
import com.ecommerce.common.integration.LoyaltyQueryService;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.RiskCheckResult;
import com.ecommerce.order.repository.OrderEventLogRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.ProductSnapshotDto;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OrderService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderEventLogRepository orderEventLogRepository;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private ProductQueryService productQueryService;
    @Mock
    private InventoryReservationService inventoryReservationService;
    @Mock
    private LoyaltyQueryService loyaltyQueryService;
    @Mock
    private LoyaltyCommandService loyaltyCommandService;
    @Mock
    private OrderPreconditionChecker preconditionChecker;
    @Mock
    private OrderValidator orderValidator;
    @Mock
    private OrderTotalCalculator totalCalculator;
    @Mock
    private OrderStateMachine stateMachine;
    @Mock
    private OrderRiskChecker riskChecker;
    @Mock
    private OrderSplitStrategy splitStrategy;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private com.ecommerce.promotion.service.PromotionCalculationService promotionCalculationService;
    @Mock
    private com.ecommerce.promotion.service.PromotionUsageCommandService promotionUsageCommandService;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest request;
    private SkuDto sku;
    private ProductSnapshotDto productSnapshot;

    @BeforeEach
    void setUp() {
        request = new CreateOrderRequest();
        request.setAddressId(10L);

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(100L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        lenient().when(splitStrategy.split(any(CreateOrderRequest.class)))
                .thenAnswer(invocation -> List.of(new OrderSplitGroup(
                        invocation.getArgument(0, CreateOrderRequest.class).getItems())));

        sku = new SkuDto();
        sku.setSkuId(100L);
        sku.setSpuId(1000L);
        sku.setSkuCode("SKU-001");
        sku.setName("Test Product");
        sku.setPrice(new BigDecimal("50.00"));

        productSnapshot = new ProductSnapshotDto();
        productSnapshot.setSkuId(100L);
        productSnapshot.setName("Test Product");
        productSnapshot.setPrice(new BigDecimal("50.00"));

        lenient().when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        lenient().when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);
        lenient().when(totalCalculator.calculateShippingFee(new BigDecimal("100.00"))).thenReturn(new BigDecimal("8.00"));
        lenient().when(totalCalculator.calculatePackagingFee(1)).thenReturn(new BigDecimal("1.00"));
        lenient().when(totalCalculator.calculate(new BigDecimal("100.00"), new BigDecimal("8.00"), new BigDecimal("1.00"),
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(new BigDecimal("109.00"));

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        lenient().when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(500L);
        savedOrder.setOrderNo("SO202606070123");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(new BigDecimal("100.00"));
        savedOrder.setShippingFee(new BigDecimal("8.00"));
        savedOrder.setPackagingFee(new BigDecimal("1.00"));
        savedOrder.setPayableAmount(new BigDecimal("109.00"));
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        lenient().when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
    }

    @Test
    @DisplayName("createOrder success returns CreateOrderResponse with correct data")
    void testCreateOrder_success_returnsOrderResponse() {
        CreateOrderResponse response = orderService.createOrder(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(500L);
        assertThat(response.getOrderNo()).isEqualTo("SO202606070123");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED.name());
        assertThat(response.getItemTotal()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.getShippingFee()).isEqualTo(new BigDecimal("8.00"));
        assertThat(response.getPayableAmount()).isEqualTo(new BigDecimal("109.00"));
    }

    @Test
    @DisplayName("createOrder with redeemed points freezes loyalty points only")
    void testCreateOrder_redeemPoints_freezesPoints() {
        request.setRedeemPoints(500);
        when(loyaltyQueryService.estimateRedeemPoints(new BigDecimal("101.00"), 1L)).thenReturn(300);
        when(totalCalculator.calculate(new BigDecimal("100.00"), new BigDecimal("8.00"), new BigDecimal("1.00"),
                BigDecimal.ZERO, new BigDecimal("3.00"))).thenReturn(new BigDecimal("106.00"));

        Order savedOrder = new Order();
        savedOrder.setId(501L);
        savedOrder.setOrderNo("SO202606070456");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(new BigDecimal("100.00"));
        savedOrder.setShippingFee(new BigDecimal("8.00"));
        savedOrder.setPackagingFee(new BigDecimal("1.00"));
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(new BigDecimal("3.00"));
        savedOrder.setPayableAmount(new BigDecimal("106.00"));
        savedOrder.setRedeemedPoints(300);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.createOrder(1L, request);

        verify(loyaltyCommandService).freezePoints(eq(1L), eq(300), eq("ORDER_REDEEM"), eq("501"), any());
        verify(loyaltyCommandService, never()).redeemPoints(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(loyaltyCommandService, never()).consumeFrozenPoints(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("createOrder marks selected coupons used in transaction")
    void testCreateOrder_marksCouponsUsed() {
        request.setCouponIds(List.of(11L, 12L));

        orderService.createOrder(1L, request);

        verify(promotionUsageCommandService).markCouponsUsed(1L, 500L, List.of(11L, 12L));
    }

    @Test
    @DisplayName("createOrder rejects high risk order before save")
    void testCreateOrder_highRisk_rejectedBeforeSave() {
        when(riskChecker.check(eq(1L), eq(new BigDecimal("100.00")), eq(List.of(100L))))
                .thenReturn(RiskCheckResult.rejected(RiskCheckResult.RiskLevel.HIGH, "high risk"));

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ORDER_RISK_REJECTED");

        verify(orderRepository, never()).save(any(Order.class));
        verify(inventoryReservationService, never()).reserve(anyLong(), anyList());
    }

    @Test
    @DisplayName("createOrder rejects unsupported multi-group split")
    void testCreateOrder_multiGroupSplit_rejected() {
        when(splitStrategy.split(any(CreateOrderRequest.class)))
                .thenReturn(List.of(new OrderSplitGroup(request.getItems()), new OrderSplitGroup(request.getItems())));

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ORDER_STATUS_CONFLICT");

        verify(productQueryService, never()).getSkuForSale(anyLong());
    }

    @Test
    @DisplayName("createOrder reserves inventory via InventoryReservationService")
    void testCreateOrder_reservesInventory() {
        orderService.createOrder(1L, request);

        ArgumentCaptor<List<ReserveItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryReservationService).reserve(eq(500L), captor.capture());

        List<ReserveItem> reservedItems = captor.getValue();
        assertThat(reservedItems).hasSize(1);
        assertThat(reservedItems.get(0).getSkuId()).isEqualTo(100L);
        assertThat(reservedItems.get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("createOrder publishes OrderCreatedEvent")
    void testCreateOrder_publishesOrderCreatedEvent() {
        orderService.createOrder(1L, request);

        verify(eventPublisher).publish(any(com.ecommerce.order.event.OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("createOrder calls preconditionChecker.check")
    void testCreateOrder_callsPreconditionCheck() {
        orderService.createOrder(1L, request);

        verify(preconditionChecker).check(1L, 1);
    }
}
