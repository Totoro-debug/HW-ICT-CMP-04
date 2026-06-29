package com.ecommerce.product.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.ProductSpuTag;
import com.ecommerce.product.entity.ProductTag;
import com.ecommerce.product.entity.SkuStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ProductSearchService.class)
@DisplayName("ProductSearchService DataJpa")
class ProductSearchServiceDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductSearchService productSearchService;

    private Category rootCategory;
    private Category childCategory;
    private Category grandChildCategory;
    private ProductTag hotTag;
    private ProductTag newTag;

    @BeforeEach
    void setUp() {
        rootCategory = persistCategory("Electronics", null, 1);
        childCategory = persistCategory("Audio", rootCategory.getId(), 2);
        grandChildCategory = persistCategory("Wireless Audio", childCategory.getId(), 3);

        hotTag = persistTag("HOT");
        newTag = persistTag("NEW");

        ProductSpu keywordSpu = persistSpu("SPU-KEYWORD", "Ultra Speaker", "Wireless home audio", 1L, childCategory.getId(), "keyword.jpg");
        persistSku(keywordSpu, "SKU-KEYWORD", "Cinema Edition", new BigDecimal("199.00"), SkuStatus.ON_SHELF, 100);

        ProductSpu taggedSpu = persistSpu("SPU-TAG", "Tagged Headset", "Immersive sound", 1L, grandChildCategory.getId(), "tagged.jpg");
        persistSku(taggedSpu, "SKU-TAGGED", "Noise Cancelling Headset", new BigDecimal("159.00"), SkuStatus.ON_SHELF, 90);
        persistSpuTag(taggedSpu.getId(), hotTag.getId());

        ProductSpu sameBrandSpu = persistSpu("SPU-BRAND", "Brand Earbuds", "Portable music", 1L, grandChildCategory.getId(), "brand.jpg");
        persistSku(sameBrandSpu, "SKU-BRAND", "Commuter Earbuds", new BigDecimal("89.00"), SkuStatus.ON_SHELF, 80);

        ProductSpu otherBrandSpu = persistSpu("SPU-OTHER-BRAND", "Other Brand Device", "Different brand", 2L, rootCategory.getId(), "other.jpg");
        persistSku(otherBrandSpu, "SKU-OTHER-BRAND", "Other Brand Device", new BigDecimal("49.00"), SkuStatus.ON_SHELF, 120);

        ProductSpu offShelfSpu = persistSpu("SPU-OFF", "Draft Product", "Not publicly visible", 1L, childCategory.getId(), "off.jpg");
        persistSku(offShelfSpu, "SKU-OFF", "Backroom Item", new BigDecimal("39.00"), SkuStatus.OFF_SHELF, 70);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("keyword search matches SKU name, SPU name, and SPU description")
    void testSearch_keywordMatchesSkuAndSpuFields() {
        ProductSearchRequest skuNameRequest = new ProductSearchRequest();
        skuNameRequest.setKeyword("headset");
        PageResponse<ProductListResponse> skuNameResult = productSearchService.search(skuNameRequest);
        assertThat(skuNameResult.getItems()).extracting(ProductListResponse::getName)
                .containsExactly("Noise Cancelling Headset");

        ProductSearchRequest spuNameRequest = new ProductSearchRequest();
        spuNameRequest.setKeyword("ultra");
        PageResponse<ProductListResponse> spuNameResult = productSearchService.search(spuNameRequest);
        assertThat(spuNameResult.getItems()).extracting(ProductListResponse::getName)
                .containsExactly("Cinema Edition");

        ProductSearchRequest descriptionRequest = new ProductSearchRequest();
        descriptionRequest.setKeyword("wireless home");
        PageResponse<ProductListResponse> descriptionResult = productSearchService.search(descriptionRequest);
        assertThat(descriptionResult.getItems()).extracting(ProductListResponse::getName)
                .containsExactly("Cinema Edition");
    }

    @Test
    @DisplayName("category filter includes descendants and applies before pagination")
    void testSearch_categoryFilterIncludesDescendantsBeforePagination() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setCategoryId(rootCategory.getId());
        request.setPage(0);
        request.setSize(2);

        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting(ProductListResponse::getName)
                .contains("Other Brand Device", "Cinema Edition");
    }

    @Test
    @DisplayName("brand filter applies before pagination and excludes other brands")
    void testSearch_brandFilterAppliesBeforePagination() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setBrandId(1L);
        request.setPage(0);
        request.setSize(2);

        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting(ProductListResponse::getName)
                .doesNotContain("Other Brand Device");
    }

    @Test
    @DisplayName("tag filter applies before pagination and keeps total accurate")
    void testSearch_tagFilterAppliesBeforePagination() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setTags(List.of("HOT"));
        request.setPage(0);
        request.setSize(1);

        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).extracting(ProductListResponse::getName)
                .containsExactly("Noise Cancelling Headset");
    }

    @Test
    @DisplayName("unknown tag returns empty page")
    void testSearch_unknownTagReturnsEmptyPage() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setTags(List.of("UNKNOWN"));

        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    private Category persistCategory(String name, Long parentId, int level) {
        Category category = new Category();
        category.setName(name);
        category.setParentId(parentId);
        category.setLevel(level);
        category.setSortOrder(level);
        category.setStatus("ACTIVE");
        return entityManager.persistFlushFind(category);
    }

    private ProductTag persistTag(String name) {
        ProductTag tag = new ProductTag();
        tag.setName(name);
        tag.setColor("#FFFFFF");
        return entityManager.persistFlushFind(tag);
    }

    private ProductSpu persistSpu(String spuCode, String name, String description, Long brandId, Long categoryId, String mainImage) {
        ProductSpu spu = new ProductSpu();
        spu.setSpuCode(spuCode);
        spu.setName(name);
        spu.setDescription(description);
        spu.setBrandId(brandId);
        spu.setCategoryId(categoryId);
        spu.setMainImage(mainImage);
        spu.setStatus("ACTIVE");
        return entityManager.persistFlushFind(spu);
    }

    private ProductSku persistSku(ProductSpu spu, String skuCode, String name, BigDecimal price, SkuStatus status, int sortOrder) {
        ProductSku sku = new ProductSku();
        sku.setSpuId(spu.getId());
        sku.setSkuCode(skuCode);
        sku.setName(name);
        sku.setPrice(price);
        sku.setStatus(status);
        sku.setSortOrder(sortOrder);
        sku.setSalesCount(0);
        return entityManager.persistFlushFind(sku);
    }

    private ProductSpuTag persistSpuTag(Long spuId, Long tagId) {
        ProductSpuTag spuTag = new ProductSpuTag();
        spuTag.setSpuId(spuId);
        spuTag.setTagId(tagId);
        return entityManager.persistFlushFind(spuTag);
    }
}
