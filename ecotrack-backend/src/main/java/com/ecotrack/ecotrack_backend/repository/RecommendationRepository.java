package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUserIdOrderByImpactEstimateDesc(Long userId);

    void deleteByUserId(Long userId);
}