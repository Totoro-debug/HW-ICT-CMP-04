package com.ecommerce.cart.service;

import com.ecommerce.cart.cache.CartCacheManager;
import com.ecommerce.cart.cache.CartData;
import com.ecommerce.cart.cache.CartItemData;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartEstimateRequest;
import com.ecommerce.cart.dto.CartEstimateResponse;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.common.integration.PointsRedeemEstimator;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.service.PromotionCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core service for shopping cart operations.
 *
 * <p>The main cart flow stores temporary cart data in Caffeine via
 * {@link CartCacheManager}. The cache configuration applies the required
 * 7-day TTL for cart entries.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private static final BigDecimal SHIPPING_FEE = new BigDecimal("8.00");
    private static final BigDecimal PACKAGING_FEE = new BigDecimal("2.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");
    private static final BigDecimal POINTS_TO_YUAN_RATE = new BigDecimal("0.01");

    private final CartCacheManager cartCacheManager;
    private final CartValidationService cartValidationService;
    private final PromotionCalculationService promotionCalculationService;
    private final PointsRedeemEstimator pointsRedeemEstimator;

    public CartService(CartCacheManager cartCacheManager,
                       CartValidationService cartValidationService,
                       PromotionCalculationService promotionCalculationService,
                       PointsRedeemEstimator pointsRedeemEstimator) {
        this.cartCacheManager = cartCacheManager;
        this.cartValidationService = cartValidationService;
        this.promotionCalculationService = promotionCalculationService;
        this.pointsRedeemEstimator = pointsRedeemEstimator;
    }

    /**
     * Adds an item to the user's cart. If the cart does not exist, it is created.
     */
    public CartItemResponse addItem(Long userId, AddCartItemRequest request) {
        log.debug("Adding item to cart: userId={}, skuId={}, quantity={}",
                userId, request.getSkuId(), request.getQuantity());

        cartValidationService.validateQuantity(request.getQuantity());
        SkuDto sku = cartValidationService.validateSku(request.getSkuId());
        cartValidationService.validateStock(request.getSkuId(), request.getQuantity());

        CartData cart = getOrCreateCart(userId);
        Optional<CartItemData> existingItem = cart.getItems().stream()
                .filter(item -> item.getSkuId().equals(request.getSkuId()))
                .findFirst();

        CartItemData item;
        if (existingItem.isPresent()) {
            item = existingItem.get();
            item.setSkuName(sku.getName());
            item.setPrice(sku.getPrice());
            item.setQuantity(request.getQuantity());
        } else {
            cartValidationService.validateCartSize(cart.getItems().size(), 1);
            item = new CartItemData(sku.getSkuId(), sku.getName(), sku.getPrice(), request.getQuantity());
            cart.getItems().add(item);
        }

        cartCacheManager.saveCart(cart);
        return toCartItemResponse(item);
    }

    /**
     * Retrieves the full cart for the given user.
     */
    public CartResponse getCart(Long userId) {
        log.debug("Getting cart for userId={}", userId);
        CartData cart = cartCacheManager.getCart(userId);
        if (cart == null) {
            return buildEmptyCartResponse();
        }
        return buildCartResponse(cart.getItems());
    }

    /**
     * Updates the quantity of an existing item in the cart.
     */
    public CartItemResponse updateItem(Long userId, Long skuId, UpdateCartItemRequest request) {
        log.debug("Updating cart item: userId={}, skuId={}, quantity={}", userId, skuId, request.getQuantity());

        cartValidationService.validateQuantity(request.getQuantity());
        SkuDto sku = cartValidationService.validateSku(skuId);
        cartValidationService.validateStock(skuId, request.getQuantity());

        CartData cart = findCartByUserId(userId);
        CartItemData item = findCartItemBySkuId(cart, skuId);

        item.setSkuName(sku.getName());
        item.setPrice(sku.getPrice());
        item.setQuantity(request.getQuantity());
        cartCacheManager.saveCart(cart);

        return toCartItemResponse(item);
    }

    /**
     * Removes a single item from the cart.
     */
    public void removeItem(Long userId, Long skuId) {
        log.debug("Removing item from cart: userId={}, skuId={}", userId, skuId);

        CartData cart = findCartByUserId(userId);
        CartItemData item = findCartItemBySkuId(cart, skuId);
        cart.getItems().remove(item);
        if (cart.getItems().isEmpty()) {
            cartCacheManager.removeCart(userId);
        } else {
            cartCacheManager.saveCart(cart);
        }
        log.debug("Removed item: skuId={} from cart of userId={}", skuId, userId);
    }

    /**
     * Clears all items from the cart.
     */
    public void clearCart(Long userId) {
        log.debug("Clearing cart for userId={}", userId);
        cartCacheManager.removeCart(userId);
    }

    /**
     * Estimates the total price for the cart including shipping, packaging,
     * promotions and points deduction. Each item is revalidated against current
     * SKU and stock state before pricing.
     */
    public CartEstimateResponse estimate(Long userId, CartEstimateRequest request) {
        log.debug("Estimating cart for userId={}, couponIds={}, redeemPoints={}",
                userId, request.getCouponIds(), request.getRedeemPoints());

        CartData cart = cartCacheManager.getCart(userId);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return emptyEstimateResponse();
        }

        BigDecimal itemTotal = BigDecimal.ZERO;
        List<PromotionCalculateRequest.CalculateItem> promotionItems = new ArrayList<>();
        List<CartItemData> refreshedItems = new ArrayList<>();
        for (CartItemData item : cart.getItems()) {
            SkuDto sku = cartValidationService.validateSku(item.getSkuId());
            cartValidationService.validateStock(item.getSkuId(), item.getQuantity());

            CartItemData refreshed = new CartItemData(sku.getSkuId(), sku.getName(), sku.getPrice(), item.getQuantity());
            refreshed.setAddedAt(item.getAddedAt());
            refreshedItems.add(refreshed);

            BigDecimal lineTotal = MonetaryUtil.multiply(sku.getPrice(), BigDecimal.valueOf(item.getQuantity()));
            itemTotal = MonetaryUtil.add(itemTotal, lineTotal);

            PromotionCalculateRequest.CalculateItem promotionItem = new PromotionCalculateRequest.CalculateItem();
            promotionItem.setSkuId(sku.getSkuId());
            promotionItem.setPrice(sku.getPrice());
            promotionItem.setQuantity(item.getQuantity());
            promotionItems.add(promotionItem);
        }
        cart.setItems(refreshedItems);
        cartCacheManager.saveCart(cart);

        BigDecimal shippingFee = itemTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIPPING_FEE;
        BigDecimal packagingFee = PACKAGING_FEE;

        BigDecimal discountAmount = calculateDiscountAmount(userId, request.getCouponIds(), promotionItems);
        BigDecimal prePointsAmount = MonetaryUtil.add(itemTotal, shippingFee);
        prePointsAmount = MonetaryUtil.add(prePointsAmount, packagingFee);
        prePointsAmount = MonetaryUtil.subtract(prePointsAmount, discountAmount);
        if (prePointsAmount.compareTo(BigDecimal.ZERO) < 0) {
            prePointsAmount = BigDecimal.ZERO;
        }

        BigDecimal pointsDeductionAmount = calculatePointsDeduction(userId, request.getRedeemPoints(), prePointsAmount);

        BigDecimal payableAmount = MonetaryUtil.subtract(prePointsAmount, pointsDeductionAmount);
        if (payableAmount.compareTo(BigDecimal.ZERO) < 0) {
            payableAmount = BigDecimal.ZERO;
        }

        CartEstimateResponse response = new CartEstimateResponse();
        response.setItemTotal(itemTotal);
        response.setShippingFee(shippingFee);
        response.setPackagingFee(packagingFee);
        response.setDiscountAmount(discountAmount);
        response.setPointsDeductionAmount(pointsDeductionAmount);
        response.setPayableAmount(payableAmount);

        log.debug("Cart estimate: itemTotal={}, shipping={}, packaging={}, discount={}, points={}, payable={}",
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount, payableAmount);
        return response;
    }

    // ---- private helpers ----

    private CartData getOrCreateCart(Long userId) {
        CartData cart = cartCacheManager.getCart(userId);
        return cart != null ? cart : new CartData(userId);
    }

    private CartData findCartByUserId(Long userId) {
        CartData cart = cartCacheManager.getCart(userId);
        if (cart == null) {
            throw new com.ecommerce.common.exception.ResourceNotFoundException("Cart for user " + userId + " not found");
        }
        return cart;
    }

    private CartItemData findCartItemBySkuId(CartData cart, Long skuId) {
        return cart.getItems().stream()
                .filter(item -> item.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "CartItem for skuId " + skuId + " not found in cart for user " + cart.getUserId()));
    }

    private CartResponse buildCartResponse(List<CartItemData> items) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItemData item : items) {
            CartItemResponse itemResponse = toCartItemResponse(item);
            itemResponses.add(itemResponse);
            totalItems += item.getQuantity();
            totalAmount = MonetaryUtil.add(totalAmount, itemResponse.getSubtotal());
        }

        return new CartResponse(itemResponses, totalItems, totalAmount);
    }

    private CartItemResponse toCartItemResponse(CartItemData item) {
        BigDecimal subtotal = MonetaryUtil.multiply(item.getPrice(), BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(item.getSkuId(), item.getSkuName(), item.getPrice(), item.getQuantity(), subtotal);
    }

    private CartResponse buildEmptyCartResponse() {
        return new CartResponse(new ArrayList<>(), 0, BigDecimal.ZERO);
    }

    private CartEstimateResponse emptyEstimateResponse() {
        CartEstimateResponse empty = new CartEstimateResponse();
        empty.setItemTotal(BigDecimal.ZERO);
        empty.setShippingFee(BigDecimal.ZERO);
        empty.setPackagingFee(BigDecimal.ZERO);
        empty.setDiscountAmount(BigDecimal.ZERO);
        empty.setPointsDeductionAmount(BigDecimal.ZERO);
        empty.setPayableAmount(BigDecimal.ZERO);
        return empty;
    }

    private BigDecimal calculateDiscountAmount(Long userId, List<Long> couponIds,
                                               List<PromotionCalculateRequest.CalculateItem> promotionItems) {
        PromotionCalculateRequest request = new PromotionCalculateRequest();
        request.setUserId(userId);
        request.setCouponIds(couponIds);
        request.setItems(promotionItems);
        PromotionCalculateResponse response = promotionCalculationService.calculate(request);
        BigDecimal discount = response != null ? response.getTotalDiscount() : null;
        return discount != null ? discount : BigDecimal.ZERO;
    }

    private BigDecimal calculatePointsDeduction(Long userId, Integer requestedPoints, BigDecimal prePointsAmount) {
        if (requestedPoints == null || requestedPoints <= 0 || prePointsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        int redeemable = pointsRedeemEstimator.estimateRedeemPoints(prePointsAmount, userId);
        int actualPoints = Math.min(requestedPoints, redeemable);
        if (actualPoints <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal deduction = MonetaryUtil.multiply(BigDecimal.valueOf(actualPoints), POINTS_TO_YUAN_RATE);
        if (deduction.compareTo(prePointsAmount) > 0) {
            return prePointsAmount;
        }
        return deduction;
    }
}
