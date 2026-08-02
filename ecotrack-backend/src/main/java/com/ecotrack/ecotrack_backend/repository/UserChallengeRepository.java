package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.UserChallenge;
import com.ecotrack.ecotrack_backend.entity.UserChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserChallengeRepository extends JpaRepository<UserChallenge, Long> {

    List<UserChallenge> findByUserId(Long userId);

    List<UserChallenge> findByUserIdAndStatus(Long userId, UserChallengeStatus status);

    @Query("SELECT uc FROM UserChallenge uc WHERE uc.user.id = :userId AND uc.challenge.challengeId = :challengeId")
    Optional<UserChallenge> findByUserIdAndChallengeId(@Param("userId") Long userId, @Param("challengeId") Long challengeId);

    @Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END FROM UserChallenge uc WHERE uc.user.id = :userId AND uc.challenge.challengeId = :challengeId")
    boolean existsByUserIdAndChallengeId(@Param("userId") Long userId, @Param("challengeId") Long challengeId);
}