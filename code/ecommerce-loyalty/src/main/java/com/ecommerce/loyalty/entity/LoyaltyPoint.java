package com.ecommerce.loyalty.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Design-model points bucket used to keep Appendix C loyalty_points in sync.
 */
@Entity
@Table(name = "loyalty_points")
public class LoyaltyPoint extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int points;

    @Column(name = "available_points", nullable = false)
    private int availablePoints;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "source_type")
    private String sourceType;

    public LoyaltyPoint() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getAvailablePoints() {
        return availablePoints;
    }

    public void setAvailablePoints(int availablePoints) {
        this.availablePoints = availablePoints;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}
