package com.ecommerce.inventory.query;

/**
 * Represents a single item to reserve in an order.
 * Used as input to {@link InventoryReservationService#reserve(Long, java.util.List)}.
 */
public class ReserveItem {

    private Long skuId;
    private int quantity;
    private String province;

    public ReserveItem() {
    }

    public ReserveItem(Long skuId, int quantity) {
        this(skuId, quantity, null);
    }

    public ReserveItem(Long skuId, int quantity, String province) {
        this.skuId = skuId;
        this.quantity = quantity;
        this.province = province;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }
}
