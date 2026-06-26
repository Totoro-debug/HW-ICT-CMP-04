package com.ecommerce.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges the product-side inventory query port to the inventory module bean
 * at the app-bootstrap composition layer.
 */
@Configuration
public class InventoryQueryBridgeConfig {

    @Bean
    public com.ecommerce.product.query.InventoryQueryService productInventoryQueryService(
            com.ecommerce.inventory.query.InventoryQueryService inventoryQueryService) {
        return skuId -> {
            com.ecommerce.inventory.query.StockSummaryDto summary = inventoryQueryService.getStockSummary(skuId);
            if (summary == null) {
                return new com.ecommerce.product.query.StockSummaryDto(0, 0);
            }
            return new com.ecommerce.product.query.StockSummaryDto(
                    summary.getAvailableStock(),
                    summary.getReservedStock());
        };
    }
}
