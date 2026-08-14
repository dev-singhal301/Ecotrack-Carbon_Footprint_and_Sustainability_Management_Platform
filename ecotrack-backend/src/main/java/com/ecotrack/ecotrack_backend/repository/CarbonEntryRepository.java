package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CarbonEntryRepository extends JpaRepository<CarbonEntry, Long> {

    long countByUserId(Long userId);
    
    List<CarbonEntry> findByUserId(Long userId);

    List<CarbonEntry> findByUserIdAndCategory(Long userId, CarbonCategory category);

    List<CarbonEntry> findByUserIdAndActivityDateBetween(Long userId, LocalDate from, LocalDate to);

    List<CarbonEntry> findByUserIdAndCategoryAndActivityDateBetween(
            Long userId, CarbonCategory category, LocalDate from, LocalDate to);

    List<CarbonEntry> findByUserIdOrderByActivityDateDescEntryIdDesc(Long userId, org.springframework.data.domain.Pageable pageable);

    // --- Added for Dashboard Aggregations ---

    /**
     * Sums total carbon emission (kg CO2e) for a specific user across all time.
     */
    @Query("SELECT COALESCE(SUM(c.carbonEmission), 0) FROM CarbonEntry c WHERE c.user.id = :userId")
    BigDecimal sumTotalCarbonEmissionByUserId(@Param("userId") Long userId);

    /**
     * Groups carbon emissions by category for a user and orders them descending (highest emitter first).
     * Returns an Object array: [0] = CarbonCategory, [1] = BigDecimal sum of emissions.
     */
    @Query("SELECT c.category, COALESCE(SUM(c.carbonEmission), 0) " +
           "FROM CarbonEntry c " +
           "WHERE c.user.id = :userId " +
           "GROUP BY c.category " +
           "ORDER BY SUM(c.carbonEmission) DESC")
    List<Object[]> findCategoryImpactByUserId(@Param("userId") Long userId);
}