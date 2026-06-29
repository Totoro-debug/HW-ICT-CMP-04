package com.ecommerce.logistics.service;

import java.math.BigDecimal;

/**
 * Context used for freight calculation at order creation time.
 */
public class FreightCalculationContext {

    private BigDecimal itemTotal;
    private Long templateId;
    private String province;
    private BigDecimal weightKg;
    private Integer itemCount;

    public FreightCalculationContext() {
    }

    public FreightCalculationContext(BigDecimal itemTotal, Long templateId, String province,
                                     BigDecimal weightKg, Integer itemCount) {
        this.itemTotal = itemTotal;
        this.templateId = templateId;
        this.province = province;
        this.weightKg = weightKg;
        this.itemCount = itemCount;
    }

    public static FreightCalculationContext of(BigDecimal itemTotal, Long templateId, String province,
                                               BigDecimal weightKg, Integer itemCount) {
        return new FreightCalculationContext(itemTotal, templateId, province, weightKg, itemCount);
    }

    public BigDecimal getItemTotal() {
        return itemTotal;
    }

    public void setItemTotal(BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }
}
