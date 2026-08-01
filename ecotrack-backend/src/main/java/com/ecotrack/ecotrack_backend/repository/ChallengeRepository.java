package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    // Newest-first for the admin management table
    List<Challenge> findAllByOrderByCreatedAtDesc();
}