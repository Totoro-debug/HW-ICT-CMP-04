package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InboundRequest;
import com.ecommerce.inventory.dto.OutboundRequest;
import com.ecommerce.inventory.dto.StockAdjustmentRequest;
import com.ecommerce.inventory.dto.StockWarningResponse;
import com.ecommerce.inventory.dto.WarehouseCreateRequest;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockAdjustment;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.StockAdjustmentService;
import com.ecommerce.inventory.service.StockWarningService;
import com.ecommerce.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final StockWarningService stockWarningService;
    private final StockAdjustmentService stockAdjustmentService;

    public AdminInventoryController(WarehouseService warehouseService,
                                    InventoryService inventoryService,
                                    StockWarningService stockWarningService,
                                    StockAdjustmentService stockAdjustmentService) {
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.stockWarningService = stockWarningService;
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public Warehouse createWarehouse(@Valid @RequestBody WarehouseCreateRequest request) {
        return warehouseService.create(request);
    }

    @PostMapping("/inventory/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStock inbound(@Valid @RequestBody InboundRequest request) {
        return inventoryService.inbound(request);
    }

    @PostMapping("/inventory/outbound")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStock outbound(@Valid @RequestBody OutboundRequest request) {
        return inventoryService.outbound(
                request.getWarehouseId(), request.getSkuId(), request.getQuantity(), request.getOrderId());
    }

    @PostMapping("/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockAdjustment createAdjustment(@Valid @RequestBody StockAdjustmentRequest request) {
        return stockAdjustmentService.create(
                request.getWarehouseId(), request.getSkuId(), request.getAfterQty(), request.getReason());
    }

    @GetMapping("/inventory/warnings")
    public List<StockWarningResponse> getWarnings() {
        return stockWarningService.getWarnings();
    }
}
