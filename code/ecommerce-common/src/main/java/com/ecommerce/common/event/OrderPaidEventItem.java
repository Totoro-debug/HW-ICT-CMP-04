package com.ecommerce.common.event;

import java.math.BigDecimal;

/**
 * Item payload carried by OrderPaidEvent.
 */
public class OrderPaidEventItem {

    private final Long skuId;
    private final Long productId;
    private final Integer quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal payableAmount;

    public OrderPaidEventItem(Long skuId, Long productId, Integer quantity,
                              BigDecimal unitPrice, BigDecimal payableAmount) {
        this.skuId = skuId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.payableAmount = payableAmount;
    }

    public Long getSkuId() {
        return skuId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }
}
