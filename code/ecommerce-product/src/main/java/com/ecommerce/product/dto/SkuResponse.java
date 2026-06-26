package com.ecommerce.product.dto;

import com.ecommerce.product.entity.ProductSku;

import java.math.BigDecimal;

/**
 * Response DTO for SKU management APIs.
 */
public class SkuResponse {

    private Long id;
    private Long spuId;
    private String skuCode;
    private String name;
    private BigDecimal price;
    private BigDecimal marketPrice;
    private String specs;
    private String image;
    private String status;
    private Integer sortOrder;
    private Integer salesCount;

    public static SkuResponse from(ProductSku sku) {
        SkuResponse response = new SkuResponse();
        response.setId(sku.getId());
        response.setSpuId(sku.getSpuId());
        response.setSkuCode(sku.getSkuCode());
        response.setName(sku.getName());
        response.setPrice(sku.getPrice());
        response.setMarketPrice(sku.getMarketPrice());
        response.setSpecs(sku.getSpecs());
        response.setImage(sku.getImage());
        response.setStatus(sku.getStatus() == null ? null : sku.getStatus().name());
        response.setSortOrder(sku.getSortOrder());
        response.setSalesCount(sku.getSalesCount());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getMarketPrice() { return marketPrice; }
    public void setMarketPrice(BigDecimal marketPrice) { this.marketPrice = marketPrice; }
    public String getSpecs() { return specs; }
    public void setSpecs(String specs) { this.specs = specs; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getSalesCount() { return salesCount; }
    public void setSalesCount(Integer salesCount) { this.salesCount = salesCount; }
}
