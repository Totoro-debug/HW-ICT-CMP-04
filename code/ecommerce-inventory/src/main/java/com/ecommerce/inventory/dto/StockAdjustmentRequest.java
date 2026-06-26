package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class StockAdjustmentRequest {

    @NotNull
    private Long warehouseId;

    @NotNull
    private Long skuId;

    private int afterQty;

    @NotBlank
    private String reason;

    public StockAdjustmentRequest() {
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public int getAfterQty() {
        return afterQty;
    }

    public void setAfterQty(int afterQty) {
        this.afterQty = afterQty;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
