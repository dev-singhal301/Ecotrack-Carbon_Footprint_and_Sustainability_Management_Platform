package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CarbonEntryRepository extends JpaRepository<CarbonEntry, Long> {

    List<CarbonEntry> findByUserId(Long userId);

    List<CarbonEntry> findByUserIdAndCategory(Long userId, CarbonCategory category);

    List<CarbonEntry> findByUserIdAndActivityDateBetween(Long userId, LocalDate from, LocalDate to);

    List<CarbonEntry> findByUserIdAndCategoryAndActivityDateBetween(
            Long userId, CarbonCategory category, LocalDate from, LocalDate to);

    List<CarbonEntry> findByUserIdOrderByActivityDateDescEntryIdDesc(Long userId, org.springframework.data.domain.Pageable pageable);
}