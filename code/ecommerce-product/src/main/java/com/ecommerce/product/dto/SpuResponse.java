package com.ecommerce.product.dto;

import com.ecommerce.product.entity.ProductSpu;

/**
 * Response DTO for SPU management APIs.
 */
public class SpuResponse {

    private Long id;
    private String spuCode;
    private String name;
    private String description;
    private Long brandId;
    private Long categoryId;
    private String mainImage;
    private String images;
    private String status;

    public static SpuResponse from(ProductSpu spu) {
        SpuResponse response = new SpuResponse();
        response.setId(spu.getId());
        response.setSpuCode(spu.getSpuCode());
        response.setName(spu.getName());
        response.setDescription(spu.getDescription());
        response.setBrandId(spu.getBrandId());
        response.setCategoryId(spu.getCategoryId());
        response.setMainImage(spu.getMainImage());
        response.setImages(spu.getImages());
        response.setStatus(spu.getStatus());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSpuCode() { return spuCode; }
    public void setSpuCode(String spuCode) { this.spuCode = spuCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getMainImage() { return mainImage; }
    public void setMainImage(String mainImage) { this.mainImage = mainImage; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
