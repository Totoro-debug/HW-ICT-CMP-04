package com.ecommerce.product.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Internal SPU-tag association for product search filtering.
 */
@Entity
@Table(name = "product_spu_tag")
public class ProductSpuTag extends BaseEntity {

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    public ProductSpuTag() {
    }

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }
}
