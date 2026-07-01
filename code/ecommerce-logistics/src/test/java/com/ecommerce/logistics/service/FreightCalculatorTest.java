package com.ecommerce.logistics.service;

import com.ecommerce.logistics.config.LogisticsProperties;
import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.repository.FreightTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FreightCalculator}.
 *
 * <p>Verifies the freight calculation logic.
 */
@ExtendWith(MockitoExtension.class)
class FreightCalculatorTest {

    @Mock
    private FreightTemplateRepository freightTemplateRepository;

    private FreightCalculator calculator;

    @BeforeEach
    void setUp() {
        lenient().when(freightTemplateRepository.findAll()).thenReturn(Collections.emptyList());
        calculator = new FreightCalculator(freightTemplateRepository, new FreightTemplateCache());
    }

    /**
     * itemTotal=199.00 qualifies for free shipping.
     * Verifies threshold boundary behavior.
     */
    @Test
    void testCalculate_exactly199_freeShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("199.00"));
        assertEquals(new BigDecimal("0.00"), result,
                "itemTotal=199.00 boundary result");
    }

    @Test
    void testCalculate_over199_freeShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("200.00"));
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void testCalculate_below199_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_usesConfiguredFreeShippingThreshold() {
        LogisticsProperties properties = new LogisticsProperties();
        properties.setFreeShippingThreshold(new BigDecimal("50.00"));
        FreightCalculator configuredCalculator = new FreightCalculator(
                freightTemplateRepository, new FreightTemplateCache(), new com.fasterxml.jackson.databind.ObjectMapper(), properties);

        BigDecimal result = configuredCalculator.calculateFreight(new BigDecimal("60.00"));

        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void testCalculate_roundsTemplateFreightHalfUpToCent() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Half Up Template");
        template.setDefaultFreight(new BigDecimal("1.235"));
        template.setFreeShippingThreshold(new BigDecimal("299.00"));

        when(freightTemplateRepository.findAll()).thenReturn(Collections.singletonList(template));

        BigDecimal result = calculator.calculateFreight(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("1.24"), result);
    }

    @Test
    void testNormalizeFreightAmount_halfCentRoundsUp() {
        assertEquals(new BigDecimal("0.01"), FreightCalculator.normalizeFreightAmount(new BigDecimal("0.005")));
    }

    @Test
    void testCalculate_zeroAmount_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(BigDecimal.ZERO);
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_nullAmount_chargesShipping() {
        BigDecimal result = calculator.calculateFreight((BigDecimal) null);
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_negativeAmount_chargesShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("-10.00"));
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_justAboveThreshold_freeShipping() {
        BigDecimal result = calculator.calculateFreight(new BigDecimal("199.01"));
        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    void testCalculate_withActiveTemplate_overridesThreshold() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Express");
        template.setDefaultFreight(new BigDecimal("15.00"));
        template.setFreeShippingThreshold(new BigDecimal("299.00"));

        when(freightTemplateRepository.findAll()).thenReturn(Collections.singletonList(template));

        // 250.00 < 299.00 threshold, so freight is charged
        BigDecimal result = calculator.calculateFreight(new BigDecimal("250.00"));
        assertEquals(new BigDecimal("15.00"), result);

        // 299.00 reaches the threshold, free shipping
        BigDecimal result2 = calculator.calculateFreight(new BigDecimal("299.00"));
        assertEquals(new BigDecimal("0.00"), result2);
    }

    @Test
    void testCalculate_withTemplateId_matchesTemplate() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Special");
        template.setDefaultFreight(new BigDecimal("20.00"));
        template.setFreeShippingThreshold(new BigDecimal("500.00"));

        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        // 400.00 < 500.00 threshold via template, charges freight
        BigDecimal result = calculator.calculateFreight(new BigDecimal("400.00"), 1L);
        assertEquals(new BigDecimal("20.00"), result);

        // 500.00 reaches the threshold, free shipping
        BigDecimal result2 = calculator.calculateFreight(new BigDecimal("500.00"), 1L);
        assertEquals(new BigDecimal("0.00"), result2);
    }

    @Test
    void testCalculate_withTemplateId_nullFallsBackToDefault() {
        when(freightTemplateRepository.findAll()).thenReturn(Collections.emptyList());

        BigDecimal result = calculator.calculateFreight(new BigDecimal("100.00"), null);
        assertEquals(new BigDecimal("8.00"), result);
    }

    @Test
    void testCalculate_withContext_matchesProvinceRuleFirst() {
        FreightTemplate template = templateWithRules();
        template.setProvinceRules("[{\"province\":\"Beijing\",\"freight\":6.00}]");
        template.setWeightRules("[{\"maxWeightKg\":5.0,\"freight\":15.00}]");
        template.setItemCountRules("[{\"maxItemCount\":10,\"freight\":18.00}]");
        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        BigDecimal result = calculator.calculateFreight(
                FreightCalculationContext.of(new BigDecimal("100.00"), 1L, "Beijing", new BigDecimal("2.0"), 3));

        assertEquals(new BigDecimal("6.00"), result);
    }

    @Test
    void testCalculate_withContext_matchesWeightRule() {
        FreightTemplate template = templateWithRules();
        template.setWeightRules("[{\"maxWeightKg\":1.0,\"freight\":8.00},{\"maxWeightKg\":5.0,\"freight\":15.00}]");
        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        BigDecimal result = calculator.calculateFreight(
                FreightCalculationContext.of(new BigDecimal("100.00"), 1L, "Shanghai", new BigDecimal("3.0"), 20));

        assertEquals(new BigDecimal("15.00"), result);
    }

    @Test
    void testCalculate_withContext_matchesItemCountRule() {
        FreightTemplate template = templateWithRules();
        template.setItemCountRules("[{\"maxItemCount\":3,\"freight\":8.00},{\"maxItemCount\":10,\"freight\":13.00}]");
        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        BigDecimal result = calculator.calculateFreight(
                FreightCalculationContext.of(new BigDecimal("100.00"), 1L, "Shanghai", null, 6));

        assertEquals(new BigDecimal("13.00"), result);
    }

    @Test
    void testCalculate_withInvalidJson_fallsBackToTemplateDefault() {
        FreightTemplate template = templateWithRules();
        template.setProvinceRules("not-json");
        template.setWeightRules("not-json");
        template.setItemCountRules("not-json");
        when(freightTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        BigDecimal result = calculator.calculateFreight(
                FreightCalculationContext.of(new BigDecimal("100.00"), 1L, "Beijing", new BigDecimal("2.0"), 2));

        assertEquals(new BigDecimal("20.00"), result);
    }

    private FreightTemplate templateWithRules() {
        FreightTemplate template = new FreightTemplate();
        template.setId(1L);
        template.setName("Context Template");
        template.setDefaultFreight(new BigDecimal("20.00"));
        template.setFreeShippingThreshold(new BigDecimal("199.00"));
        return template;
    }
}
