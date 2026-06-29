package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.MemberLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Internal member-benefit resolver. Keeps benefit definitions separate from
 * the member-level enum while preserving existing REST response fields.
 */
@Service
public class MemberBenefitService {

    public MemberBenefits getBenefits(MemberLevel level) {
        return switch (level) {
            case PLATINUM -> new MemberBenefits(new BigDecimal("1.5"), List.of("POINTS_MULTIPLIER_1_5", "PRIORITY_SERVICE"));
            case GOLD -> new MemberBenefits(new BigDecimal("1.2"), List.of("POINTS_MULTIPLIER_1_2", "MEMBER_PROMOTION"));
            case SILVER -> new MemberBenefits(new BigDecimal("1.1"), List.of("POINTS_MULTIPLIER_1_1"));
            case NORMAL -> new MemberBenefits(BigDecimal.ONE, List.of("POINTS_MULTIPLIER_1_0"));
        };
    }

    public BigDecimal getPointsMultiplier(MemberLevel level) {
        return getBenefits(level).pointsMultiplier();
    }

    public record MemberBenefits(BigDecimal pointsMultiplier, List<String> benefitCodes) {
    }
}
