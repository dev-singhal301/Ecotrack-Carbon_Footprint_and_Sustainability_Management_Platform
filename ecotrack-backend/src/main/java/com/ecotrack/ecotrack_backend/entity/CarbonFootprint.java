package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "carbon_footprint")
public class CarbonFootprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "footprint_id")
    private Long footprintId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "period", nullable = false, length = 50)
    private String period;

    @Column(name = "total_emission")
    private BigDecimal totalEmission = BigDecimal.ZERO;

    @Column(name = "electricity_emission")
    private BigDecimal electricityEmission = BigDecimal.ZERO;

    @Column(name = "transportation_emission")
    private BigDecimal transportationEmission = BigDecimal.ZERO;

    @Column(name = "fuel_emission")
    private BigDecimal fuelEmission = BigDecimal.ZERO;

    @Column(name = "water_emission")
    private BigDecimal waterEmission = BigDecimal.ZERO;

    @Column(name = "waste_emission")
    private BigDecimal wasteEmission = BigDecimal.ZERO;

    @Column(name = "food_emission")
    private BigDecimal foodEmission = BigDecimal.ZERO;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public CarbonFootprint() {}

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getFootprintId() { return footprintId; }
    public void setFootprintId(Long footprintId) { this.footprintId = footprintId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public BigDecimal getTotalEmission() { return totalEmission; }
    public void setTotalEmission(BigDecimal totalEmission) { this.totalEmission = totalEmission; }

    public BigDecimal getElectricityEmission() { return electricityEmission; }
    public void setElectricityEmission(BigDecimal electricityEmission) { this.electricityEmission = electricityEmission; }

    public BigDecimal getTransportationEmission() { return transportationEmission; }
    public void setTransportationEmission(BigDecimal transportationEmission) { this.transportationEmission = transportationEmission; }

    public BigDecimal getFuelEmission() { return fuelEmission; }
    public void setFuelEmission(BigDecimal fuelEmission) { this.fuelEmission = fuelEmission; }

    public BigDecimal getWaterEmission() { return waterEmission; }
    public void setWaterEmission(BigDecimal waterEmission) { this.waterEmission = waterEmission; }

    public BigDecimal getWasteEmission() { return wasteEmission; }
    public void setWasteEmission(BigDecimal wasteEmission) { this.wasteEmission = wasteEmission; }

    public BigDecimal getFoodEmission() { return foodEmission; }
    public void setFoodEmission(BigDecimal foodEmission) { this.foodEmission = foodEmission; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}