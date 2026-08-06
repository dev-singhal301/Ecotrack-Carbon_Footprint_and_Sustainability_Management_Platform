package com.ecotrack.ecotrack_backend.repository;

import com.ecotrack.ecotrack_backend.entity.ChallengeActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChallengeActivityLogRepository extends JpaRepository<ChallengeActivityLog, Long> {

    List<ChallengeActivityLog> findByUserChallengeId(Long userChallengeId);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END FROM ChallengeActivityLog l " +
           "WHERE l.userChallenge.id = :userChallengeId AND l.logDate = :logDate")
    boolean existsByUserChallengeIdAndLogDate(@Param("userChallengeId") Long userChallengeId,
                                               @Param("logDate") LocalDate logDate);
}
