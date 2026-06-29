package com.ecommerce.logistics.service;

import com.ecommerce.common.integration.FreightCalculationService;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
public class FreightCalculator implements FreightCalculationService {

    private static final Logger log = LoggerFactory.getLogger(FreightCalculator.class);

    private static final BigDecimal DEFAULT_FREIGHT = new BigDecimal("8.00");
    private static final BigDecimal DEFAULT_FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");
    private static final TypeReference<List<Map<String, Object>>> RULE_LIST_TYPE = new TypeReference<>() {
    };

    private final FreightTemplateRepository freightTemplateRepository;
    private final FreightTemplateCache freightTemplateCache;
    private final ObjectMapper objectMapper;

    @Autowired
    public FreightCalculator(FreightTemplateRepository freightTemplateRepository,
                             FreightTemplateCache freightTemplateCache) {
        this(freightTemplateRepository, freightTemplateCache, new ObjectMapper());
    }

    FreightCalculator(FreightTemplateRepository freightTemplateRepository,
                      FreightTemplateCache freightTemplateCache,
                      ObjectMapper objectMapper) {
        this.freightTemplateRepository = freightTemplateRepository;
        this.freightTemplateCache = freightTemplateCache;
        this.objectMapper = objectMapper;
    }

    /**
     * Calculate the freight for an order based on item total.
     *
     * @param itemTotal the total price of items in the order
     * @return the freight amount (0.00 if free shipping applies)
     */
    public BigDecimal calculateFreight(BigDecimal itemTotal) {
        return calculateFreight(new FreightCalculationContext(itemTotal, null, null, null, null));
    }

    /**
     * Calculate freight for a specific item total and template ID.
     */
    public BigDecimal calculateFreight(BigDecimal itemTotal, Long templateId) {
        return calculateFreight(new FreightCalculationContext(itemTotal, templateId, null, null, null));
    }

    @Override
    public BigDecimal calculateFreight(BigDecimal itemTotal, String province, BigDecimal weightKg, Integer itemCount) {
        return calculateFreight(new FreightCalculationContext(itemTotal, null, province, weightKg, itemCount));
    }

    /**
     * Calculate freight with order creation context, including province, weight and item count.
     */
    public BigDecimal calculateFreight(FreightCalculationContext context) {
        FreightCalculationContext safeContext = context != null
                ? context : new FreightCalculationContext(null, null, null, null, null);
        BigDecimal itemTotal = safeContext.getItemTotal();
        if (itemTotal == null || itemTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return normalizeFreightAmount(DEFAULT_FREIGHT);
        }

        FreightTemplate template = safeContext.getTemplateId() == null
                ? findActiveTemplate()
                : freightTemplateCache.get(safeContext.getTemplateId(),
                        () -> freightTemplateRepository.findById(safeContext.getTemplateId()))
                .orElse(null);

        if (template != null) {
            return calculateWithTemplate(safeContext, template);
        }

        return calculateDefault(itemTotal);
    }

    private BigDecimal calculateDefault(BigDecimal itemTotal) {
        if (itemTotal.compareTo(DEFAULT_FREE_SHIPPING_THRESHOLD) >= 0) {
            log.info("Free shipping (default): itemTotal={} reaches threshold={}",
                    itemTotal, DEFAULT_FREE_SHIPPING_THRESHOLD);
            return normalizeFreightAmount(BigDecimal.ZERO);
        }
        log.info("Freight charged (default): itemTotal={}, freight={}", itemTotal, DEFAULT_FREIGHT);
        return normalizeFreightAmount(DEFAULT_FREIGHT);
    }

    private BigDecimal calculateWithTemplate(FreightCalculationContext context, FreightTemplate template) {
        BigDecimal itemTotal = context.getItemTotal();
        BigDecimal threshold = template.getFreeShippingThreshold() != null
                ? template.getFreeShippingThreshold() : DEFAULT_FREE_SHIPPING_THRESHOLD;
        BigDecimal defaultFreight = template.getDefaultFreight() != null
                ? template.getDefaultFreight() : DEFAULT_FREIGHT;

        if (itemTotal != null && itemTotal.compareTo(threshold) >= 0) {
            log.info("Free shipping: itemTotal={} reaches threshold={}", itemTotal, threshold);
            return normalizeFreightAmount(BigDecimal.ZERO);
        }

        BigDecimal freight = matchProvinceRule(template.getProvinceRules(), context.getProvince());
        if (freight == null) {
            freight = matchWeightRule(template.getWeightRules(), context.getWeightKg());
        }
        if (freight == null) {
            freight = matchItemCountRule(template.getItemCountRules(), context.getItemCount());
        }
        if (freight == null) {
            freight = defaultFreight;
        }

        log.info("Freight charged: itemTotal={}, threshold={}, freight={}", itemTotal, threshold, freight);
        return normalizeFreightAmount(freight);
    }

    private BigDecimal matchProvinceRule(String rulesJson, String province) {
        if (province == null || province.isBlank()) {
            return null;
        }
        for (Map<String, Object> rule : parseRules(rulesJson, "provinceRules")) {
            Object ruleProvince = rule.get("province");
            if (ruleProvince != null && province.equals(String.valueOf(ruleProvince))) {
                return toBigDecimal(rule.get("freight"));
            }
        }
        return null;
    }

    private BigDecimal matchWeightRule(String rulesJson, BigDecimal weightKg) {
        if (weightKg == null) {
            return null;
        }
        return parseRules(rulesJson, "weightRules").stream()
                .filter(rule -> toBigDecimal(rule.get("maxWeightKg")) != null)
                .sorted(Comparator.comparing(rule -> toBigDecimal(rule.get("maxWeightKg"))))
                .filter(rule -> weightKg.compareTo(toBigDecimal(rule.get("maxWeightKg"))) <= 0)
                .map(rule -> toBigDecimal(rule.get("freight")))
                .filter(amount -> amount != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal matchItemCountRule(String rulesJson, Integer itemCount) {
        if (itemCount == null) {
            return null;
        }
        return parseRules(rulesJson, "itemCountRules").stream()
                .filter(rule -> toInteger(rule.get("maxItemCount")) != null)
                .sorted(Comparator.comparing(rule -> toInteger(rule.get("maxItemCount"))))
                .filter(rule -> itemCount <= toInteger(rule.get("maxItemCount")))
                .map(rule -> toBigDecimal(rule.get("freight")))
                .filter(amount -> amount != null)
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> parseRules(String rulesJson, String ruleName) {
        if (rulesJson == null || rulesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rulesJson, RULE_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse {} freight rule JSON, fallback to default freight: {}", ruleName, e.getMessage());
            return List.of();
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static BigDecimal normalizeFreightAmount(BigDecimal amount) {
        if (amount == null) {
            return new BigDecimal("0.00");
        }
        return MonetaryUtil.roundToCent(amount);
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
