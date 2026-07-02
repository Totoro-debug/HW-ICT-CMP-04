package com.ecommerce.order.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.math.BigDecimal;

/**
 * A single line item within an order.
 */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id"),
        @Index(name = "idx_order_items_sku_id", columnList = "sku_id")
})
public class OrderItem extends BaseEntity {

    /** Parent order ID */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** SKU ID at time of purchase */
    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** Product name snapshot at time of purchase */
    @Column(name = "product_name", nullable = false, length = 256)
    private String productName;

    /** SKU code snapshot at time of purchase */
    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    /** Unit price at time of purchase */
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /** Quantity ordered */
    @Column(nullable = false)
    private int quantity;

    /** Line amount: unit price * quantity */
    @Column(name = "item_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal itemAmount;

    /** SKU specs snapshot at order time */
    @Column(name = "sku_specs", columnDefinition = "CLOB")
    private String skuSpecs;

    public OrderItem() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSkuName() {
        return productName;
    }

    public void setSkuName(String skuName) {
        this.productName = skuName;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getPrice() {
        return unitPrice;
    }

    public void setPrice(BigDecimal price) {
        this.unitPrice = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getItemAmount() {
        return itemAmount;
    }

    public void setItemAmount(BigDecimal itemAmount) {
        this.itemAmount = itemAmount;
    }

    public BigDecimal getSubtotal() {
        return itemAmount;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.itemAmount = subtotal;
    }

    public String getSkuSpecs() {
        return skuSpecs;
    }

    public void setSkuSpecs(String skuSpecs) {
        this.skuSpecs = skuSpecs;
    }

    public String getProductSnapshot() {
        return skuSpecs;
    }

    public void setProductSnapshot(String productSnapshot) {
        this.skuSpecs = productSnapshot;
    }
}
