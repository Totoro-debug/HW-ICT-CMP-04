package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.cache.InventorySummaryCache;
import com.ecommerce.inventory.dto.InboundRequest;
import com.ecommerce.inventory.dto.InventoryCheckResponse;
import com.ecommerce.inventory.dto.StockSummaryResponse;
import com.ecommerce.inventory.entity.InboundOrder;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.OutboundOrder;
import com.ecommerce.inventory.query.InventoryQueryService;
import com.ecommerce.inventory.query.StockSummaryDto;
import com.ecommerce.inventory.repository.InboundOrderRepository;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.OutboundOrderRepository;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core inventory service handling inbound, outbound, stock queries,
 * and availability checks.
 *
 * <p>Implements {@link InventoryQueryService}, the query contract provided by
 * the inventory module to other modules.
 */
@Service
public class InventoryService implements InventoryQueryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryStockRepository inventoryStockRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final ProductQueryService productQueryService;

    public InventoryService(InventoryStockRepository inventoryStockRepository,
                            InboundOrderRepository inboundOrderRepository,
                            OutboundOrderRepository outboundOrderRepository,
                            ProductQueryService productQueryService) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.inboundOrderRepository = inboundOrderRepository;
        this.outboundOrderRepository = outboundOrderRepository;
        this.productQueryService = productQueryService;
    }

    // ---- InventoryQueryService ----

    @Override
    @Transactional(readOnly = true)
    public StockSummaryDto getStockSummary(Long skuId) {
        InventorySummaryCache.StockSnapshot snapshot = getStockSnapshot(skuId);
        return new StockSummaryDto(snapshot.availableStock(), snapshot.reservedStock());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkAvailability(Long skuId, int quantity) {
        int totalAvailable = getStockSnapshot(skuId).availableStock();

        boolean available = totalAvailable > quantity;

        log.debug("checkAvailability skuId={}, quantity={}, totalAvailable={}, available={}",
                skuId, quantity, totalAvailable, available);
        return available;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listAvailableWarehouses(Long skuId) {
        return inventoryStockRepository.findBySkuId(skuId).stream()
                .filter(s -> s.getAvailableStock() > 0)
                .map(InventoryStock::getWarehouseId)
                .collect(Collectors.toList());
    }

    // ---- business operations for controllers ----

    @Transactional(readOnly = true)
    public StockSummaryResponse getStockSummaryResponse(Long skuId) {
        // Fault injection checks
        if (com.ecommerce.common.test.FaultInjectionRegistry.isActive("inventory-query-service-unavailable")) {
            throw new RuntimeException("Fault injected: inventory-query-service-unavailable");
        }
        if (com.ecommerce.common.test.FaultInjectionRegistry.isActive("product-query-service-unavailable")) {
            throw new RuntimeException("Fault injected: product-query-service-unavailable");
        }

        InventorySummaryCache.StockSnapshot snapshot = getStockSnapshot(skuId);

        // Uses ProductQueryService to get product name — correct cross-module pattern
        SkuDto skuDto = productQueryService.getSku(skuId);
        String skuName = skuDto != null ? skuDto.getName() : null;

        StockSummaryResponse response = new StockSummaryResponse();
        response.setSkuId(skuId);
        response.setSkuName(skuName);
        response.setOnHandStock(snapshot.onHandStock());
        response.setReservedStock(snapshot.reservedStock());
        response.setAvailableStock(snapshot.availableStock());
        return response;
    }

    @Transactional(readOnly = true)
    public InventoryCheckResponse checkAndReport(Long skuId, int quantity) {
        boolean available = checkAvailability(skuId, quantity);
        int totalAvailable = getStockSnapshot(skuId).availableStock();
        return new InventoryCheckResponse(skuId, available, totalAvailable);
    }

    @Transactional
    public InventoryStock inbound(InboundRequest request) {
        InventoryStock stock = inventoryStockRepository
                .findByWarehouseIdAndSkuId(request.getWarehouseId(), request.getSkuId())
                .orElseGet(() -> {
                    InventoryStock newStock = new InventoryStock();
                    newStock.setWarehouseId(request.getWarehouseId());
                    newStock.setSkuId(request.getSkuId());
                    newStock.setOnHandStock(0);
                    newStock.setReservedStock(0);
                    newStock.setSafetyStock(0);
                    return newStock;
                });

        stock.setOnHandStock(stock.getOnHandStock() + request.getQuantity());

        InboundOrder order = new InboundOrder();
        order.setOrderNo("IB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        order.setWarehouseId(request.getWarehouseId());
        order.setSkuId(request.getSkuId());
        order.setQuantity(request.getQuantity());
        order.setStatus("COMPLETED");
        inboundOrderRepository.save(order);

        InventoryStock saved = inventoryStockRepository.save(stock);
        InventorySummaryCache.evict(request.getSkuId());
        log.info("Inbound completed: warehouseId={}, skuId={}, qty={}",
                request.getWarehouseId(), request.getSkuId(), request.getQuantity());
        return saved;
    }

    @Transactional
    public InventoryStock outbound(Long warehouseId, Long skuId, int quantity, Long orderId) {
        InventoryStock stock = inventoryStockRepository
                .findByWarehouseIdAndSkuId(warehouseId, skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "InventoryStock", "warehouse=" + warehouseId + ", sku=" + skuId));

        if (stock.getOnHandStock() < quantity) {
            throw new BusinessException("INVENTORY_NOT_ENOUGH",
                    "Not enough on-hand stock for skuId=" + skuId + " in warehouseId=" + warehouseId);
        }

        stock.setOnHandStock(stock.getOnHandStock() - quantity);

        OutboundOrder order = new OutboundOrder();
        order.setOrderNo("OB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        order.setWarehouseId(warehouseId);
        order.setSkuId(skuId);
        order.setQuantity(quantity);
        order.setOrderId(orderId);
        order.setStatus("COMPLETED");
        outboundOrderRepository.save(order);

        InventoryStock saved = inventoryStockRepository.save(stock);
        InventorySummaryCache.evict(skuId);
        log.info("Outbound completed: warehouseId={}, skuId={}, qty={}, orderId={}",
                warehouseId, skuId, quantity, orderId);
        return saved;
    }

    private InventorySummaryCache.StockSnapshot getStockSnapshot(Long skuId) {
        return InventorySummaryCache.get(skuId, this::loadStockSnapshot);
    }

    private InventorySummaryCache.StockSnapshot loadStockSnapshot(Long skuId) {
        List<InventoryStock> stocks = inventoryStockRepository.findBySkuId(skuId);
        int totalOnHand = stocks.stream().mapToInt(InventoryStock::getOnHandStock).sum();
        int totalReserved = stocks.stream().mapToInt(InventoryStock::getReservedStock).sum();
        int totalAvailable = totalOnHand - totalReserved;
        return new InventorySummaryCache.StockSnapshot(totalOnHand, totalReserved, totalAvailable);
    }
}
