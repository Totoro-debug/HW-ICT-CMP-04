package com.ecommerce.promotion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the promotion module.
 */
@Configuration
@EnableConfigurationProperties(PromotionProperties.class)
public class PromotionModuleConfig {
}
