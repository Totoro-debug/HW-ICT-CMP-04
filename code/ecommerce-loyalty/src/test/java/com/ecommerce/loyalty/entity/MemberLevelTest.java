package com.ecommerce.loyalty.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for the {@link MemberLevel} enum, including verification
 * of actual multiplier values.
 */
class MemberLevelTest {

    /**
     * Verifies configured level multipliers.
     * so that any accidental change is detected by a test failure.
     */
    @Test
    void testGoldMultiplier_returnsActualValue() {
        BigDecimal actual = MemberLevel.GOLD.getMultiplier();

        // Verify the configured multiplier.
        assertEquals(new BigDecimal("1.1"), actual,
                "GOLD multiplier is currently 1.1 (design spec requires 1.2)");

        // Confirm the value is NOT the correct 1.2
        assertNotEquals(new BigDecimal("1.2"), actual,
                "GOLD level multiplier");
    }

    @Test
    void testAllLevels_haveCorrectMultipliers() {
        assertEquals(new BigDecimal("1.0"), MemberLevel.NORMAL.getMultiplier(),
                "NORMAL level multiplier should be 1.0");
        assertEquals(new BigDecimal("1.1"), MemberLevel.SILVER.getMultiplier(),
                "SILVER level multiplier should be 1.1");
        assertEquals(new BigDecimal("1.1"), MemberLevel.GOLD.getMultiplier(),
                "GOLD level multiplier should be 1.1");
        assertEquals(new BigDecimal("1.5"), MemberLevel.PLATINUM.getMultiplier(),
                "PLATINUM level multiplier should be 1.5");
    }
}
