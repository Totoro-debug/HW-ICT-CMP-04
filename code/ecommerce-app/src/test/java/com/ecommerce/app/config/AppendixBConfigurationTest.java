package com.ecommerce.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppendixBConfigurationTest {

    @Test
    void mainConfigurationContainsAppendixBRequiredKeys() throws IOException {
        PropertySource<?> source = loadYaml("application.yml");

        assertEquals(100, source.getProperty("invoice.max-title-length"));
        assertEquals("LOCAL_EXPRESS", source.getProperty("logistics.default-carrier"));
        assertEquals(199.00, source.getProperty("logistics.free-shipping-threshold"));
        assertEquals("FULL_REDUCTION", source.getProperty("promotion.stack-order[0]"));
        assertEquals("COUPON", source.getProperty("promotion.stack-order[1]"));
        assertEquals("MEMBER_DISCOUNT", source.getProperty("promotion.stack-order[2]"));
        assertEquals(false, source.getProperty("test.reset-enabled"));
    }

    @Test
    void testConfigurationMatchesAppendixBJwtExampleAndRequiredKeys() throws IOException {
        PropertySource<?> source = loadYaml("application-test.yml");

        assertEquals("shophub", source.getProperty("security.jwt.issuer"));
        assertEquals("local-development-secret-change-me", source.getProperty("security.jwt.secret"));
        assertEquals(120, source.getProperty("security.jwt.expire-minutes"));
        assertEquals(100, source.getProperty("invoice.max-title-length"));
        assertEquals("LOCAL_EXPRESS", source.getProperty("logistics.default-carrier"));
        assertEquals(199.00, source.getProperty("logistics.free-shipping-threshold"));
        assertEquals("FULL_REDUCTION", source.getProperty("promotion.stack-order[0]"));
        assertEquals("COUPON", source.getProperty("promotion.stack-order[1]"));
        assertEquals("MEMBER_DISCOUNT", source.getProperty("promotion.stack-order[2]"));
        assertEquals(false, source.getProperty("test.reset-enabled"));
    }

    private PropertySource<?> loadYaml(String path) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(path, new ClassPathResource(path));
        return sources.get(0);
    }
}
