package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.CarbonFootprint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CarbonFootprintRepository extends JpaRepository<CarbonFootprint, Long> {
    Optional<CarbonFootprint> findByUserIdAndPeriod(Long userId, String period);

    /**
     * Fetches daily snapshots for a user from a given period (inclusive) onward, oldest first.
     * Powers the emissions trend chart (relative "last N days" mode). Relies on period being
     * stored as an ISO yyyy-MM-dd string, so string comparison/ordering matches chronological order.
     */
    List<CarbonFootprint> findByUserIdAndPeriodGreaterThanEqualOrderByPeriodAsc(Long userId, String period);

    /**
     * Fetches daily snapshots for a user between two periods (inclusive), oldest first.
     * Powers the custom start/end date range on the trend chart. Same ISO string-ordering
     * assumption as above.
     */
    List<CarbonFootprint> findByUserIdAndPeriodBetweenOrderByPeriodAsc(Long userId, String periodFrom, String periodTo);
}