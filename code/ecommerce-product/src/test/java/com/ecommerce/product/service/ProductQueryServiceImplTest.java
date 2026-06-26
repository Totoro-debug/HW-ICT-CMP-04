package com.ecommerce.product.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("ProductQueryServiceImpl")
@ExtendWith(MockitoExtension.class)
class ProductQueryServiceImplTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Test
    @DisplayName("getSkuForSale throws PRODUCT_NOT_FOR_SALE for non-ON_SHELF SKU")
    void getSkuForSale_nonOnShelf_throwsProductNotForSale() {
        ProductQueryServiceImpl service = new ProductQueryServiceImpl(skuRepository, new ObjectMapper());
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
}
