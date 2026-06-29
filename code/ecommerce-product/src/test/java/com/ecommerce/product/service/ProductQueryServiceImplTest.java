package com.ecommerce.product.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("ProductQueryServiceImpl")
@ExtendWith(MockitoExtension.class)
class ProductQueryServiceImplTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private ProductSpuRepository spuRepository;

    @Test
    @DisplayName("getSkuForSale throws PRODUCT_NOT_FOR_SALE for non-ON_SHELF SKU")
    void getSkuForSale_nonOnShelf_throwsProductNotForSale() {
        ProductQueryServiceImpl service = new ProductQueryServiceImpl(skuRepository, spuRepository, new ObjectMapper());
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setSpuId(1L);
        sku.setSkuCode("SKU-10");
        sku.setName("Off shelf SKU");
        sku.setPrice(new BigDecimal("19.90"));
        sku.setStatus(SkuStatus.OFF_SHELF);
        when(skuRepository.findById(10L)).thenReturn(Optional.of(sku));

        assertThatThrownBy(() -> service.getSkuForSale(10L))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("PRODUCT_NOT_FOR_SALE"));
    }

    @Test
    @DisplayName("getCategoryIdsBySkuIds returns category ids from SPUs")
    void getCategoryIdsBySkuIds_returnsCategoryIds() {
        ProductQueryServiceImpl service = new ProductQueryServiceImpl(skuRepository, spuRepository, new ObjectMapper());

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setSpuId(100L);
        ProductSpu spu = new ProductSpu();
        spu.setId(100L);
        spu.setCategoryId(200L);

        when(skuRepository.findByIdIn(List.of(10L))).thenReturn(List.of(sku));
        when(spuRepository.findById(100L)).thenReturn(Optional.of(spu));

        assertThat(service.getCategoryIdsBySkuIds(List.of(10L))).containsExactly(200L);
    }
}
