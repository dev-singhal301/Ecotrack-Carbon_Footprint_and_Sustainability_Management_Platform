package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "emission_factors")
public class EmissionFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emission_factor_id")
    private Long emissionFactorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private CarbonCategory category;

    @Column(nullable = false, length = 50)
    private String unit;

    @Column(name = "factor_value", nullable = false)
    private BigDecimal factorValue;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public EmissionFactor() {
    }

    public Long getEmissionFactorId() {
        return emissionFactorId;
    }

    public void setEmissionFactorId(Long emissionFactorId) {
        this.emissionFactorId = emissionFactorId;
    }

    public CarbonCategory getCategory() {
        return category;
    }

    public void setCategory(CarbonCategory category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getFactorValue() {
        return factorValue;
    }

    public void setFactorValue(BigDecimal factorValue) {
        this.factorValue = factorValue;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}