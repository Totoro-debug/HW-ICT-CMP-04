package com.ecommerce.product.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.ecommerce.product.repository.ProductSpuTagRepository;
import com.ecommerce.product.repository.ProductTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductSearchService")
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private ProductSpuRepository spuRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductTagRepository productTagRepository;

    @Mock
    private ProductSpuTagRepository productSpuTagRepository;

    @InjectMocks
    private ProductSearchService productSearchService;

    private ProductSku onShelfSku;
    private ProductSku offShelfSku;
    private ProductSku draftSku;
    private ProductSpu spu;

    @BeforeEach
    void setUp() {
        spu = new ProductSpu();
        spu.setId(1L);
        spu.setName("Test SPU");
        spu.setDescription("Great wireless performance");
        spu.setCategoryId(10L);
        spu.setBrandId(100L);
        spu.setMainImage("main.jpg");

        onShelfSku = new ProductSku();
        onShelfSku.setId(1L);
        onShelfSku.setSpuId(1L);
        onShelfSku.setName("OnShelf SKU");
        onShelfSku.setPrice(new BigDecimal("99.99"));
        onShelfSku.setStatus(SkuStatus.ON_SHELF);
        onShelfSku.setSortOrder(10);
        onShelfSku.setSalesCount(5);

        offShelfSku = new ProductSku();
        offShelfSku.setId(2L);
        offShelfSku.setSpuId(1L);
        offShelfSku.setName("OffShelf SKU");
        offShelfSku.setPrice(new BigDecimal("49.99"));
        offShelfSku.setStatus(SkuStatus.OFF_SHELF);
        offShelfSku.setSortOrder(5);
        offShelfSku.setSalesCount(0);

        draftSku = new ProductSku();
        draftSku.setId(3L);
        draftSku.setSpuId(1L);
        draftSku.setName("Draft SKU");
        draftSku.setPrice(new BigDecimal("29.99"));
        draftSku.setStatus(SkuStatus.DRAFT);
        draftSku.setSortOrder(0);
        draftSku.setSalesCount(0);
    }

    @Test
    @DisplayName("search defaults to only ON_SHELF products")
    void testSearch_defaultOnlyOnShelf_returnsOnlyOnShelfSkus() {
        Page<ProductSku> page = new PageImpl<>(List.of(onShelfSku));
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        assertThat(request.isOnlyOnShelf()).isTrue();

        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().stream().map(ProductListResponse::getStatus))
                .containsExactly("ON_SHELF");
    }

    @Test
    @DisplayName("search with onlyOnShelf=true passes a specification to repository")
    void testSearch_withOnlyOnShelfTrue_filtersToOnShelf() {
        List<ProductSku> allSkus = List.of(onShelfSku, offShelfSku, draftSku);
        Page<ProductSku> page = new PageImpl<>(allSkus);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setOnlyOnShelf(true);
        productSearchService.search(request);

        ArgumentCaptor<Specification<ProductSku>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(skuRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("search by keyword still maps repository results")
    void testSearch_byKeyword_returnsMatchedSkus() {
        ProductSku matchingSku = new ProductSku();
        matchingSku.setId(10L);
        matchingSku.setSpuId(1L);
        matchingSku.setName("Premium Widget");
        matchingSku.setPrice(new BigDecimal("199.99"));
        matchingSku.setStatus(SkuStatus.ON_SHELF);
        matchingSku.setSortOrder(1);
        matchingSku.setSalesCount(0);

        Page<ProductSku> page = new PageImpl<>(List.of(matchingSku));
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword("widget");
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Premium Widget");
    }

    @Test
    @DisplayName("search by categoryId returns empty page when category does not exist")
    void testSearch_byCategoryId_returnsEmptyWhenCategoryMissing() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        ProductSearchRequest request = new ProductSearchRequest();
        request.setCategoryId(999L);
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
        verify(skuRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search by price range filters SKUs with price between min and max")
    void testSearch_byPriceRange_filtersByPrice() {
        List<ProductSku> filteredSkus = List.of(onShelfSku);
        Page<ProductSku> page = new PageImpl<>(filteredSkus);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setMinPrice(new BigDecimal("30.00"));
        request.setMaxPrice(new BigDecimal("100.00"));
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("OnShelf SKU");
    }

    @Test
    @DisplayName("search by unknown tags returns empty page without querying SKUs")
    void testSearch_byUnknownTags_returnsEmptyPage() {
        when(productTagRepository.findIdsByNames(List.of("missing"))).thenReturn(List.of());

        ProductSearchRequest request = new ProductSearchRequest();
        request.setTags(List.of("missing"));
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
        verify(skuRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search pagination returns correct page metadata")
    void testSearch_pagination_returnsCorrectPage() {
        List<ProductSku> skus = List.of(onShelfSku, offShelfSku);
        Page<ProductSku> page = new PageImpl<>(skus, PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "sortOrder")), 10);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setPage(1);
        request.setSize(2);
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(10L);
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("search by categoryId traverses descendant categories before querying")
    void testSearch_byCategoryId_resolvesDescendantsBeforeQuery() {
        Category parent = new Category();
        parent.setId(20L);
        Category child = new Category();
        child.setId(21L);

        when(categoryRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findByParentId(20L)).thenReturn(List.of(child));
        when(categoryRepository.findByParentId(21L)).thenReturn(List.of());
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(onShelfSku)));
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setCategoryId(20L);
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        verify(categoryRepository).findByParentId(20L);
        verify(categoryRepository).findByParentId(21L);
    }
}
