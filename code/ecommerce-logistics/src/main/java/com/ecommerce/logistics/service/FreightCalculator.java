package com.ecommerce.logistics.service;

import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Calculates shipping freight based on item total and freight templates.
 *
 * <p>Default rules:
 * <ul>
 *   <li>Default freight: 8.00</li>
 *   <li>Free shipping when item total reaches the free-shipping threshold</li>
 * </ul>
 */
@Service
public class FreightCalculator {

    private static final Logger log = LoggerFactory.getLogger(FreightCalculator.class);

    private static final BigDecimal DEFAULT_FREIGHT = new BigDecimal("8.00");
    private static final BigDecimal DEFAULT_FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");

    private final FreightTemplateRepository freightTemplateRepository;
    private final FreightTemplateCache freightTemplateCache;

    public FreightCalculator(FreightTemplateRepository freightTemplateRepository,
                             FreightTemplateCache freightTemplateCache) {
        this.freightTemplateRepository = freightTemplateRepository;
        this.freightTemplateCache = freightTemplateCache;
    }

    /**
     * Calculate the freight for an order based on item total.
     *
     * @param itemTotal the total price of items in the order
     * @return the freight amount (0.00 if free shipping applies)
     */
    public BigDecimal calculateFreight(BigDecimal itemTotal) {
        if (itemTotal == null || itemTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_FREIGHT;
        }

        FreightTemplate template = findActiveTemplate();

        if (template != null) {
            return calculateWithTemplate(itemTotal, template);
        }

        // Fallback to default rules
        if (itemTotal.compareTo(DEFAULT_FREE_SHIPPING_THRESHOLD) >= 0) {
            log.info("Free shipping (default): itemTotal={} reaches threshold={}",
                    itemTotal, DEFAULT_FREE_SHIPPING_THRESHOLD);
            return BigDecimal.ZERO;
        }
        log.info("Freight charged (default): itemTotal={}, freight={}", itemTotal, DEFAULT_FREIGHT);
        return DEFAULT_FREIGHT;
    }

    /**
     * Calculate freight for a specific item total and template ID.
     */
    public BigDecimal calculateFreight(BigDecimal itemTotal, Long templateId) {
        if (templateId == null) {
            return calculateFreight(itemTotal);
        }

        return freightTemplateCache.get(templateId, () -> freightTemplateRepository.findById(templateId))
                .map(template -> calculateWithTemplate(itemTotal, template))
                .orElseGet(() -> calculateFreight(itemTotal));
    }

    private BigDecimal calculateWithTemplate(BigDecimal itemTotal, FreightTemplate template) {
        BigDecimal threshold = template.getFreeShippingThreshold() != null
                ? template.getFreeShippingThreshold() : DEFAULT_FREE_SHIPPING_THRESHOLD;
        BigDecimal freight = template.getDefaultFreight() != null
                ? template.getDefaultFreight() : DEFAULT_FREIGHT;

        if (itemTotal != null && itemTotal.compareTo(threshold) >= 0) {
            log.info("Free shipping: itemTotal={} reaches threshold={}", itemTotal, threshold);
            return BigDecimal.ZERO;
        }
        log.info("Freight charged: itemTotal={}, threshold={}, freight={}", itemTotal, threshold, freight);
        return freight;
    }

    private FreightTemplate findActiveTemplate() {
        FreightTemplate template = freightTemplateRepository.findAll()
                .stream()
                .findFirst()
                .orElse(null);
        if (template == null || template.getId() == null) {
            return template;
        }
        return freightTemplateCache.get(template.getId(), () -> freightTemplateRepository.findById(template.getId()))
                .orElse(template);
    }
}
