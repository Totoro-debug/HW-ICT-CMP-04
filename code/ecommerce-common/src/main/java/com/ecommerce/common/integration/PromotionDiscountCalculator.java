package com.ecommerce.common.integration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Local integration port for modules that need promotion discount estimation
 * without depending on the promotion module implementation package.
 */
public interface PromotionDiscountCalculator {

    BigDecimal calculateDiscount(Long userId, List<Item> items, List<Long> couponIds);

    class Item {
        private Long skuId;
        private BigDecimal price;
        private Integer quantity;

        public Item() {
        }

        public Item(Long skuId, BigDecimal price, Integer quantity) {
            this.skuId = skuId;
            this.price = price;
            this.quantity = quantity;
        }

        public Long getSkuId() {
            return skuId;
        }

        public void setSkuId(Long skuId) {
            this.skuId = skuId;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
