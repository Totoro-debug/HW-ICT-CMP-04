package com.ecommerce.product.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.ProductSpuTag;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.ecommerce.product.repository.ProductTagRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles product search with keyword, category, brand, price range, and tag filters.
 *
 * <p>The default public search behavior returns only ON_SHELF products because
 * {@link ProductSearchRequest#isOnlyOnShelf()} defaults to {@code true}.
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProductSkuRepository skuRepository;
    private final ProductSpuRepository spuRepository;
    private final CategoryRepository categoryRepository;
    private final ProductTagRepository productTagRepository;

    public ProductSearchService(ProductSkuRepository skuRepository,
                                ProductSpuRepository spuRepository,
                                CategoryRepository categoryRepository,
                                ProductTagRepository productTagRepository) {
        this.skuRepository = skuRepository;
        this.spuRepository = spuRepository;
        this.categoryRepository = categoryRepository;
        this.productTagRepository = productTagRepository;
    }

    /**
     * Searches for products matching the given criteria.
     *
     * <p>By default the public search returns only ON_SHELF SKUs.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductListResponse> search(ProductSearchRequest request) {
        log.debug("Product search: keyword={}, categoryId={}, brandId={}, tags={}, onlyOnShelf={}",
                request.getKeyword(), request.getCategoryId(), request.getBrandId(), request.getTags(), request.isOnlyOnShelf());

        Set<Long> categoryIds = resolveCategoryIds(request.getCategoryId());
        if (request.getCategoryId() != null && categoryIds.isEmpty()) {
            return PageResponse.of(request.getPage(), request.getSize(), 0, List.of());
        }

        Set<Long> tagIds = resolveTagIds(request.getTags());
        if (hasRequestedTags(request) && tagIds.isEmpty()) {
            return PageResponse.of(request.getPage(), request.getSize(), 0, List.of());
        }

        Specification<ProductSku> spec = buildSpecification(request, categoryIds, tagIds);

        PageRequest pageRequest = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(Sort.Direction.DESC, "sortOrder"));

        Page<ProductSku> page = skuRepository.findAll(spec, pageRequest);

        List<Long> spuIds = page.getContent().stream()
                .map(ProductSku::getSpuId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductSpu> spuMap = spuRepository.findAllById(spuIds).stream()
                .collect(Collectors.toMap(ProductSpu::getId, spu -> spu));

        List<ProductListResponse> items = page.getContent().stream()
                .map(sku -> toListResponse(sku, spuMap.get(sku.getSpuId())))
                .collect(Collectors.toList());

        return PageResponse.of(request.getPage(), request.getSize(), page.getTotalElements(), items);
    }

    private Specification<ProductSku> buildSpecification(ProductSearchRequest request,
                                                         Set<Long> categoryIds,
                                                         Set<Long> tagIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.isOnlyOnShelf()) {
                predicates.add(cb.equal(root.get("status"), SkuStatus.ON_SHELF));
            } else {
                predicates.add(cb.notEqual(root.get("status"), SkuStatus.DELETED));
            }

            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String keyword = "%" + request.getKeyword().trim().toLowerCase() + "%";
                Predicate skuNameLike = cb.like(cb.lower(root.get("name")), keyword);

                Subquery<Long> spuKeywordSubquery = query.subquery(Long.class);
                var spuRoot = spuKeywordSubquery.from(ProductSpu.class);
                Predicate sameSpu = cb.equal(spuRoot.get("id"), root.get("spuId"));
                Predicate spuNameLike = cb.like(cb.lower(spuRoot.get("name")), keyword);
                Predicate spuDescriptionLike = cb.like(cb.lower(spuRoot.get("description")), keyword);
                spuKeywordSubquery.select(spuRoot.get("id"))
                        .where(cb.and(sameSpu, cb.or(spuNameLike, spuDescriptionLike)));

                predicates.add(cb.or(skuNameLike, cb.exists(spuKeywordSubquery)));
            }

            if (request.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), request.getMinPrice()));
            }

            if (request.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), request.getMaxPrice()));
            }

            if (request.getBrandId() != null) {
                Subquery<Long> brandSubquery = query.subquery(Long.class);
                var spuRoot = brandSubquery.from(ProductSpu.class);
                brandSubquery.select(spuRoot.get("id"))
                        .where(
                                cb.equal(spuRoot.get("id"), root.get("spuId")),
                                cb.equal(spuRoot.get("brandId"), request.getBrandId())
                        );
                predicates.add(cb.exists(brandSubquery));
            }

            if (categoryIds != null && !categoryIds.isEmpty()) {
                Subquery<Long> categorySubquery = query.subquery(Long.class);
                var spuRoot = categorySubquery.from(ProductSpu.class);
                categorySubquery.select(spuRoot.get("id"))
                        .where(
                                cb.equal(spuRoot.get("id"), root.get("spuId")),
                                spuRoot.get("categoryId").in(categoryIds)
                        );
                predicates.add(cb.exists(categorySubquery));
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                Subquery<Long> tagSubquery = query.subquery(Long.class);
                var spuTagRoot = tagSubquery.from(ProductSpuTag.class);
                tagSubquery.select(spuTagRoot.get("spuId"))
                        .where(
                                cb.equal(spuTagRoot.get("spuId"), root.get("spuId")),
                                spuTagRoot.get("tagId").in(tagIds)
                        );
                predicates.add(cb.exists(tagSubquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Set<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .map(category -> {
                    Set<Long> categoryIds = new LinkedHashSet<>();
                    collectCategoryIds(category.getId(), categoryIds);
                    return categoryIds;
                })
                .orElseGet(Set::of);
    }

    private void collectCategoryIds(Long categoryId, Set<Long> categoryIds) {
        if (!categoryIds.add(categoryId)) {
            return;
        }
        for (Category child : categoryRepository.findByParentId(categoryId)) {
            collectCategoryIds(child.getId(), categoryIds);
        }
    }

    private Set<Long> resolveTagIds(List<String> tags) {
        if (!hasRequestedTags(tags)) {
            return null;
        }
        List<String> normalizedTags = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
        if (normalizedTags.isEmpty()) {
            return null;
        }
        return productTagRepository.findIdsByNames(normalizedTags).stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasRequestedTags(ProductSearchRequest request) {
        return hasRequestedTags(request.getTags());
    }

    private boolean hasRequestedTags(List<String> tags) {
        return tags != null && tags.stream().anyMatch(tag -> tag != null && !tag.isBlank());
    }

    private ProductListResponse toListResponse(ProductSku sku, ProductSpu spu) {
        ProductListResponse response = new ProductListResponse();
        response.setSkuId(sku.getId());
        response.setSpuId(sku.getSpuId());
        response.setName(sku.getName());
        response.setPrice(sku.getPrice());
        response.setStatus(sku.getStatus().name());
        response.setMainImage(spu != null ? spu.getMainImage() : sku.getImage());
        response.setSalesCount(sku.getSalesCount());
        return response;
    }
}
