package com.ecommerce.inventory.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.cache.InventorySummaryCache;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockAdjustment;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.StockAdjustmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(StockAdjustmentService.class);
    private static final String BIZ_TYPE_STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";

    private final InventoryStockRepository inventoryStockRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final AuditLogService auditLogService;

    public StockAdjustmentService(InventoryStockRepository inventoryStockRepository,
                                  StockAdjustmentRepository stockAdjustmentRepository) {
        this(inventoryStockRepository, stockAdjustmentRepository, null);
    }

    @Autowired
    public StockAdjustmentService(InventoryStockRepository inventoryStockRepository,
                                  StockAdjustmentRepository stockAdjustmentRepository,
                                  AuditLogService auditLogService) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public StockAdjustment create(Long warehouseId, Long skuId, int afterQty, String reason) {
        InventoryStock stock = inventoryStockRepository
                .findByWarehouseIdAndSkuId(warehouseId, skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "InventoryStock", "warehouse=" + warehouseId + ", sku=" + skuId));

        int beforeQty = stock.getOnHandStock();
        stock.setOnHandStock(afterQty);
        inventoryStockRepository.save(stock);

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setWarehouseId(warehouseId);
        adjustment.setSkuId(skuId);
        adjustment.setBeforeQty(beforeQty);
        adjustment.setAfterQty(afterQty);
        adjustment.setReason(reason);
        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);
        InventorySummaryCache.evict(skuId);
        recordAudit(saved, beforeQty, afterQty, reason);

        log.info("Stock adjusted: warehouseId={}, skuId={}, {} -> {}, reason={}",
                warehouseId, skuId, beforeQty, afterQty, reason);
        return saved;
    }

    private void recordAudit(StockAdjustment adjustment, int beforeQty, int afterQty, String reason) {
        if (auditLogService == null) {
            return;
        }
        String operator = currentOperator();
        auditLogService.record(operator, operator, "STOCK_ADJUSTMENT", BIZ_TYPE_STOCK_ADJUSTMENT,
                String.valueOf(adjustment.getId()), String.valueOf(beforeQty), String.valueOf(afterQty), reason);
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    @Transactional(readOnly = true)
    public List<StockAdjustment> list(Long warehouseId) {
        return stockAdjustmentRepository.findByWarehouseId(warehouseId);
    }
}
