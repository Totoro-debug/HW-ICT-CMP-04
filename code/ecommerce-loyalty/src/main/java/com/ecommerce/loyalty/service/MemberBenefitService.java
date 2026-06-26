package com.ecommerce.loyalty.service;

import com.ecommerce.loyalty.entity.MemberLevel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Internal member-benefit resolver. Keeps benefit definitions separate from
 * the member-level enum while preserving existing REST response fields.
 */
@Service
public class MemberBenefitService {

    public MemberBenefits getBenefits(MemberLevel level) {
        return switch (level) {
            case PLATINUM -> new MemberBenefits(1.5, List.of("POINTS_MULTIPLIER_1_5", "PRIORITY_SERVICE"));
            case GOLD -> new MemberBenefits(1.1, List.of("POINTS_MULTIPLIER_1_1", "MEMBER_PROMOTION"));
            case SILVER -> new MemberBenefits(1.1, List.of("POINTS_MULTIPLIER_1_1"));
            case NORMAL -> new MemberBenefits(1.0, List.of("POINTS_MULTIPLIER_1_0"));
        };
    }

    public double getPointsMultiplier(MemberLevel level) {
        return getBenefits(level).pointsMultiplier();
    }

    public record MemberBenefits(double pointsMultiplier, List<String> benefitCodes) {
    }
}
