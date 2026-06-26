package com.ecommerce.cart.service;

import com.ecommerce.cart.cache.CartCacheManager;
import com.ecommerce.cart.cache.CartData;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartEstimateRequest;
import com.ecommerce.cart.dto.CartEstimateResponse;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.integration.PointsRedeemEstimator;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.service.PromotionCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CartService")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    @Mock
    private CartCacheManager cartCacheManager;

    @Mock
    private CartValidationService cartValidationService;

    @Mock
    private PromotionCalculationService promotionCalculationService;

    @Mock
    private PointsRedeemEstimator pointsRedeemEstimator;

    private CartService cartService;

    private static final Long USER_ID = 1L;
    private static final Long SKU_ID = 100L;

    private SkuDto skuDto;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartCacheManager, cartValidationService,
                promotionCalculationService, pointsRedeemEstimator);
        skuDto = new SkuDto();
        skuDto.setSkuId(SKU_ID);
        skuDto.setName("Test SKU");
        skuDto.setPrice(new BigDecimal("25.00"));
        skuDto.setStatus("ON_SHELF");
    }

    @Test
    @DisplayName("addItem writes a new item to Caffeine cart cache")
    void testAddItem_newSku_usesCache() {
        AddCartItemRequest request = new AddCartItemRequest(SKU_ID, 3);

        doNothing().when(cartValidationService).validateQuantity(3);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 3);
        when(cartCacheManager.getCart(USER_ID)).thenReturn(null);
        doNothing().when(cartValidationService).validateCartSize(0, 1);

        CartItemResponse response = cartService.addItem(USER_ID, request);

        assertThat(response.getSkuId()).isEqualTo(SKU_ID);
        assertThat(response.getSkuName()).isEqualTo("Test SKU");
        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("75.00"));
        verify(cartCacheManager).saveCart(any(CartData.class));
    }

    @Test
    @DisplayName("adding same SKU replaces quantity in cached cart")
    void testAddItem_existingSku_replacesQuantity() {
        CartData cart = new CartData(USER_ID);
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(SKU_ID, "Old SKU", new BigDecimal("20.00"), 3));
        AddCartItemRequest request = new AddCartItemRequest(SKU_ID, 2);

        doNothing().when(cartValidationService).validateQuantity(2);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 2);
        when(cartCacheManager.getCart(USER_ID)).thenReturn(cart);

        CartItemResponse response = cartService.addItem(USER_ID, request);

        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(cart.getItems()).hasSize(1);
        verify(cartCacheManager).saveCart(cart);
    }

    @Test
    @DisplayName("getCart returns cached items with computed totals")
    void testGetCart_returnsCachedItemsWithTotals() {
        CartData cart = new CartData(USER_ID);
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(100L, "Item A", new BigDecimal("10.00"), 2));
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(200L, "Item B", new BigDecimal("15.00"), 1));
        when(cartCacheManager.getCart(USER_ID)).thenReturn(cart);

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalItems()).isEqualTo(3);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("35.00"));
    }

    @Test
    @DisplayName("updateItem revalidates SKU and stock before updating cache")
    void testUpdateItem_revalidatesAndUpdatesCache() {
        CartData cart = new CartData(USER_ID);
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(SKU_ID, "Test SKU", new BigDecimal("25.00"), 1));
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);

        doNothing().when(cartValidationService).validateQuantity(5);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 5);
        when(cartCacheManager.getCart(USER_ID)).thenReturn(cart);

        CartItemResponse response = cartService.updateItem(USER_ID, SKU_ID, request);

        assertThat(response.getQuantity()).isEqualTo(5);
        verify(cartValidationService).validateSku(SKU_ID);
        verify(cartValidationService).validateStock(SKU_ID, 5);
        verify(cartCacheManager).saveCart(cart);
    }

    @Test
    @DisplayName("removeItem removes cache entry when last item is removed")
    void testRemoveItem_lastItem_removesCartCache() {
        CartData cart = new CartData(USER_ID);
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(SKU_ID, "Test SKU", new BigDecimal("25.00"), 1));
        when(cartCacheManager.getCart(USER_ID)).thenReturn(cart);

        cartService.removeItem(USER_ID, SKU_ID);

        assertThat(cart.getItems()).isEmpty();
        verify(cartCacheManager).removeCart(USER_ID);
    }

    @Test
    @DisplayName("removeItem throws ResourceNotFoundException when cart does not exist")
    void testRemoveItem_cartNotFound_throwsException() {
        when(cartCacheManager.getCart(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, SKU_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("clearCart removes the cached cart")
    void testClearCart_removesCache() {
        cartService.clearCart(USER_ID);

        verify(cartCacheManager).removeCart(USER_ID);
    }

    @Test
    @DisplayName("estimate revalidates cart, applies promotion discounts and points")
    void testEstimate_appliesPromotionAndPoints() {
        CartData cart = new CartData(USER_ID);
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(SKU_ID, "Cached SKU", new BigDecimal("10.00"), 2));
        when(cartCacheManager.getCart(USER_ID)).thenReturn(cart);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 2);

        PromotionCalculateResponse promotionResponse = new PromotionCalculateResponse();
        promotionResponse.setTotalDiscount(new BigDecimal("10.00"));
        doReturn(promotionResponse).when(promotionCalculationService).calculate(any());
        doReturn(500).when(pointsRedeemEstimator).estimateRedeemPoints(any(), any());

        CartEstimateRequest request = new CartEstimateRequest();
        request.setCouponIds(List.of(1L));
        request.setRedeemPoints(300);

        CartEstimateResponse response = cartService.estimate(USER_ID, request);

        assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.getShippingFee()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(response.getPackagingFee()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.getPointsDeductionAmount()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(response.getPayableAmount()).isEqualByComparingTo(new BigDecimal("47.00"));
    }

    @Test
    @DisplayName("estimate returns zero for empty cached cart")
    void testEstimate_emptyCart_returnsZero() {
        when(cartCacheManager.getCart(USER_ID)).thenReturn(null);

        CartEstimateResponse response = cartService.estimate(USER_ID, new CartEstimateRequest());

        assertThat(response.getItemTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPackagingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPointsDeductionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("estimate propagates standardized validation errors")
    void testEstimate_validationError_propagatesStandardCode() {
        CartData cart = new CartData(USER_ID);
        cart.getItems().add(new com.ecommerce.cart.cache.CartItemData(SKU_ID, "Cached SKU", new BigDecimal("10.00"), 2));
        when(cartCacheManager.getCart(USER_ID)).thenReturn(cart);
        when(cartValidationService.validateSku(SKU_ID))
                .thenThrow(new BusinessException("PRODUCT_NOT_FOR_SALE", "not for sale"));

        assertThatThrownBy(() -> cartService.estimate(USER_ID, new CartEstimateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PRODUCT_NOT_FOR_SALE");
    }
}
