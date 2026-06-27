package com.ecommerce.product.service;

import com.ecommerce.common.audit.AuditLogService;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.product.dto.SkuCreateRequest;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for managing SKU (Stock Keeping Unit) operations.
 */
@Service
public class SkuService {

    private static final Logger log = LoggerFactory.getLogger(SkuService.class);
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final String BIZ_TYPE_SKU = "PRODUCT_SKU";

    private final ProductSkuRepository skuRepository;
    private final ProductSpuRepository spuRepository;
    private final ObjectMapper objectMapper;
    private final ProductDetailCacheService productDetailCacheService;
    private final AuditLogService auditLogService;

    public SkuService(ProductSkuRepository skuRepository,
                      ProductSpuRepository spuRepository,
                      ObjectMapper objectMapper,
                      ProductDetailCacheService productDetailCacheService) {
        this(skuRepository, spuRepository, objectMapper, productDetailCacheService, null);
    }

    @Autowired
    public SkuService(ProductSkuRepository skuRepository,
                      ProductSpuRepository spuRepository,
                      ObjectMapper objectMapper,
                      ProductDetailCacheService productDetailCacheService,
                      AuditLogService auditLogService) {
        this.skuRepository = skuRepository;
        this.spuRepository = spuRepository;
        this.objectMapper = objectMapper;
        this.productDetailCacheService = productDetailCacheService;
        this.auditLogService = auditLogService;
    }

    /**
     * Creates a new SKU under an existing SPU.
     */
    @Transactional
    public ProductSku createSku(SkuCreateRequest request) {
        validatePrice("price", request.getPrice());
        if (request.getMarketPrice() != null) {
            validatePrice("marketPrice", request.getMarketPrice());
        }

        if (!spuRepository.existsById(request.getSpuId())) {
            throw new ResourceNotFoundException("ProductSpu", request.getSpuId());
        }

        if (skuRepository.findBySkuCode(request.getSkuCode()).isPresent()) {
            throw new ValidationException("skuCode", "SKU code already exists: " + request.getSkuCode());
        }

        ProductSku sku = new ProductSku();
        sku.setSpuId(request.getSpuId());
        sku.setSkuCode(request.getSkuCode());
        sku.setName(request.getName());
        sku.setPrice(MonetaryUtil.roundToCent(request.getPrice()));
        sku.setMarketPrice(request.getMarketPrice() != null ? MonetaryUtil.roundToCent(request.getMarketPrice()) : null);

        if (request.getSpecs() != null && !request.getSpecs().isEmpty()) {
            try {
                sku.setSpecs(objectMapper.writeValueAsString(request.getSpecs()));
            } catch (JsonProcessingException e) {
                throw new ValidationException("specs", "Failed to serialize specs map");
            }
        }

        sku.setImage(request.getImage());
        sku.setStatus(SkuStatus.DRAFT);
        sku.setSortOrder(0);
        sku.setSalesCount(0);

        ProductSku saved = skuRepository.save(sku);
        productDetailCacheService.evict(saved.getId());
        log.info("Created SKU: id={}, skuCode={}, spuId={}", saved.getId(), saved.getSkuCode(), saved.getSpuId());
        return saved;
    }

    /**
     * Puts a SKU on shelf, making it available for sale.
     */
    @Transactional
    public void onShelf(Long skuId) {
        ProductSku sku = findSku(skuId);
        if (sku.getStatus() == SkuStatus.DELETED) {
            throw new ValidationException("status", "Cannot put a DELETED SKU on shelf");
        }
        SkuStatus beforeStatus = sku.getStatus();
        sku.setStatus(SkuStatus.ON_SHELF);
        skuRepository.save(sku);
        recordAudit("SKU_ON_SHELF", sku, beforeStatus, SkuStatus.ON_SHELF, "SKU put on shelf");
        productDetailCacheService.evict(skuId);
        log.info("SKU on shelf: skuId={}, skuCode={}", skuId, sku.getSkuCode());
    }

    /**
     * Takes a SKU off shelf, making it unavailable for sale.
     */
    @Transactional
    public void offShelf(Long skuId) {
        ProductSku sku = findSku(skuId);
        if (sku.getStatus() == SkuStatus.DELETED) {
            throw new ValidationException("status", "Cannot take a DELETED SKU off shelf");
        }
        SkuStatus beforeStatus = sku.getStatus();
        sku.setStatus(SkuStatus.OFF_SHELF);
        skuRepository.save(sku);
        recordAudit("SKU_OFF_SHELF", sku, beforeStatus, SkuStatus.OFF_SHELF, "SKU taken off shelf");
        productDetailCacheService.evict(skuId);
        log.info("SKU off shelf: skuId={}, skuCode={}", skuId, sku.getSkuCode());
    }

    private void validatePrice(String field, BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_PRICE) < 0) {
            throw new ValidationException(field, "must be at least 0.01");
        }
    }

    private void recordAudit(String operationType, ProductSku sku, SkuStatus beforeStatus,
                             SkuStatus afterStatus, String remark) {
        if (auditLogService == null) {
            return;
        }
        String operator = currentOperator();
        auditLogService.record(operator, operator, operationType, BIZ_TYPE_SKU,
                String.valueOf(sku.getId()), statusName(beforeStatus), statusName(afterStatus), remark);
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private String statusName(SkuStatus status) {
        return status != null ? status.name() : null;
    }

    private ProductSku findSku(Long skuId) {
        return skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductSku", skuId));
    }
}
