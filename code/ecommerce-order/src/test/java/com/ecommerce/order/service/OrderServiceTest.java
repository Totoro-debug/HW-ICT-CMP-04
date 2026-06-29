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
import com.ecommerce.user.query.AddressDto;
import com.ecommerce.user.query.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderEventLogRepository orderEventLogRepository;
    @Mock private UserQueryService userQueryService;
    @Mock private ProductQueryService productQueryService;
    @Mock private InventoryReservationService inventoryReservationService;
    @Mock private LoyaltyQueryService loyaltyQueryService;
    @Mock private LoyaltyCommandService loyaltyCommandService;
    @Mock private OrderPreconditionChecker preconditionChecker;
    @Mock private OrderValidator orderValidator;
    @Mock private OrderTotalCalculator totalCalculator;
    @Mock private OrderStateMachine stateMachine;
    @Mock private OrderRiskChecker riskChecker;
    @Mock private OrderSplitStrategy splitStrategy;
    @Mock private DomainEventPublisher eventPublisher;
    @Mock private com.ecommerce.promotion.service.PromotionCalculationService promotionCalculationService;
    @Mock private com.ecommerce.promotion.service.PromotionUsageCommandService promotionUsageCommandService;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateOrderRequest();
        request.setAddressId(10L);
        request.setExternalOrderNo("EXT-ORDER-001");
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(100L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        lenient().when(splitStrategy.split(any(CreateOrderRequest.class)))
                .thenAnswer(invocation -> List.of(new OrderSplitGroup(
                        invocation.getArgument(0, CreateOrderRequest.class).getItems())));
        lenient().when(orderRepository.findByExternalOrderNoAndUserId("EXT-ORDER-001", 1L))
                .thenReturn(Optional.empty());

        SkuDto sku = new SkuDto();
        sku.setSkuId(100L);
        sku.setSpuId(1000L);
        sku.setSkuCode("SKU-001");
        sku.setName("Test Product");
        sku.setPrice(new BigDecimal("50.00"));
        lenient().when(productQueryService.getSkuForSale(100L)).thenReturn(sku);

        ProductSnapshotDto snapshot = new ProductSnapshotDto();
        snapshot.setSkuId(100L);
        snapshot.setName("Test Product");
        snapshot.setPrice(new BigDecimal("50.00"));
        lenient().when(productQueryService.getProductSnapshot(100L)).thenReturn(snapshot);

        lenient().when(totalCalculator.calculateShippingFee(new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("8.00"));
        lenient().when(totalCalculator.calculatePackagingFee(1))
                .thenReturn(new BigDecimal("1.00"));
        lenient().when(totalCalculator.calculate(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("109.00"));

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        lenient().when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        AddressDto address = new AddressDto();
        address.setProvince("Guangdong");
        address.setCity("Shenzhen");
        address.setDistrict("Nanshan");
        address.setDetail("Science Park");
        lenient().when(userQueryService.getDefaultAddress(1L)).thenReturn(address);

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
    @DisplayName("createOrder reserves inventory before risk check and binds after save")
    void testCreateOrder_reserveBeforeRiskCheckAndBind() {
        CreateOrderResponse response = orderService.createOrder(1L, request);

        assertThat(response.getOrderId()).isEqualTo(500L);
        ArgumentCaptor<List<ReserveItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryReservationService).reserve(eq("ORDER:1:EXT-ORDER-001"), captor.capture());
        verify(inventoryReservationService).bindReservation("ORDER:1:EXT-ORDER-001", 500L);
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getProvince()).isEqualTo("Guangdong");
    }

    @Test
    @DisplayName("createOrder releases reservation when risk check rejects order")
    void testCreateOrder_releasesReservationOnRiskReject() {
        when(riskChecker.check(eq(1L), eq(new BigDecimal("100.00")), eq(List.of(100L))))
                .thenReturn(RiskCheckResult.rejected(RiskCheckResult.RiskLevel.HIGH, "high risk"));

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ORDER_RISK_REJECTED");

        verify(inventoryReservationService).reserve(eq("ORDER:1:EXT-ORDER-001"), any());
        verify(inventoryReservationService).release("ORDER:1:EXT-ORDER-001");
        verify(orderRepository, never()).save(any(Order.class));
    }
}
