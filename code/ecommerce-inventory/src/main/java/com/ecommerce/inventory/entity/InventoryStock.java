package com.ecommerce.inventory.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "inventory_stock", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "warehouse_id", "sku_id" })
})
public class InventoryStock extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "on_hand_stock")
    private int onHandStock;

    @Column(name = "reserved_stock")
    private int reservedStock;

    @Column(name = "warning_threshold")
    private int warningThreshold;

    public InventoryStock() {
    }

    /**
     * Returns the available stock: onHandStock minus reservedStock.
     */
    public int getAvailableStock() {
        return onHandStock - reservedStock;
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

    public int getOnHandStock() {
        return onHandStock;
    }

    public void setOnHandStock(int onHandStock) {
        this.onHandStock = onHandStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(int reservedStock) {
        this.reservedStock = reservedStock;
    }

    public int getWarningThreshold() {
        return warningThreshold;
    }

    public void setWarningThreshold(int warningThreshold) {
        this.warningThreshold = warningThreshold;
    }

    public int getSafetyStock() {
        return warningThreshold;
    }

    public void setSafetyStock(int safetyStock) {
        this.warningThreshold = safetyStock;
    }
}
