package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.CarbonFootprint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CarbonFootprintRepository extends JpaRepository<CarbonFootprint, Long> {
    Optional<CarbonFootprint> findByUserIdAndPeriod(Long userId, String period);
}