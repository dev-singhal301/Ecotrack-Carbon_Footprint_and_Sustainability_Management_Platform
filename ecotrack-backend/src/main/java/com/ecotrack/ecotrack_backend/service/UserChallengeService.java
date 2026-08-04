package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.ChallengeResponse;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.*;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserChallengeService {

    private final ChallengeRepository challengeRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final UserRepository userRepository;
    private final UserXpService userXpService;

    public UserChallengeService(
            ChallengeRepository challengeRepository,
            UserChallengeRepository userChallengeRepository,
            UserRepository userRepository,
            UserXpService userXpService) {
        this.challengeRepository = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.userRepository = userRepository;
        this.userXpService = userXpService;
    }

    public List<UserChallengeResponse> getUserChallenges(String userEmail) {
        User user = findUserByEmailOrThrow(userEmail);

        // Fetch all non-cancelled challenges
        List<Challenge> challenges = challengeRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> !c.isCancelled())
                .toList();

        // Fetch all user participation entries
        Map<Long, UserChallenge> userChallengeMap = userChallengeRepository.findByUserId(user.getId())
                .stream()
                .collect(Collectors.toMap(uc -> uc.getChallenge().getChallengeId(), Function.identity()));

        return challenges.stream()
                .map(challenge -> {
                    ChallengeResponse challengeDto = ChallengeResponse.fromEntity(challenge);
                    UserChallenge userChallenge = userChallengeMap.get(challenge.getChallengeId());
                    return UserChallengeResponse.fromEntity(challengeDto, userChallenge);
                })
                .toList();
    }

    @Transactional
    public UserChallengeResponse joinChallenge(String userEmail, Long challengeId) {
        User user = findUserByEmailOrThrow(userEmail);
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Challenge not found: " + challengeId));

        if (challenge.isCancelled()) {
            throw new IllegalStateException("Cannot join a cancelled challenge");
        }

        if (LocalDate.now().isAfter(challenge.getEndDate())) {
            throw new IllegalStateException("Cannot join a challenge that has already ended");
        }

        if (userChallengeRepository.existsByUserIdAndChallengeId(user.getId(), challengeId)) {
            throw new IllegalStateException("User has already joined this challenge");
        }

        UserChallenge userChallenge = new UserChallenge();
        userChallenge.setUser(user);
        userChallenge.setChallenge(challenge);
        userChallenge.setStatus(UserChallengeStatus.IN_PROGRESS);
        userChallenge.setCurrentValue(BigDecimal.ZERO);
        userChallenge.setCompletionPercentage(BigDecimal.ZERO);
        userChallenge.setJoinedAt(LocalDateTime.now());

        UserChallenge saved = userChallengeRepository.save(userChallenge);
        return UserChallengeResponse.fromEntity(ChallengeResponse.fromEntity(challenge), saved);
    }

    @Transactional
    public UserChallengeResponse claimReward(String userEmail, Long challengeId) {
        User user = findUserByEmailOrThrow(userEmail);
        UserChallenge userChallenge = userChallengeRepository.findByUserIdAndChallengeId(user.getId(), challengeId)
                .orElseThrow(() -> new IllegalArgumentException("User challenge record not found"));

        if (userChallenge.getCompletionPercentage().compareTo(new BigDecimal("100")) < 0) {
            throw new IllegalStateException("Challenge is not fully completed yet");
        }

        if (userChallenge.getStatus() == UserChallengeStatus.COMPLETED) {
            throw new IllegalStateException("Reward already claimed for this challenge");
        }

        userChallenge.setStatus(UserChallengeStatus.COMPLETED);
        userChallenge.setCompletedAt(LocalDateTime.now());
        UserChallenge saved = userChallengeRepository.save(userChallenge);

        // Award XP and Reward Points via UserXpService
        int xpReward = userChallenge.getChallenge().getRewardXp() != null ? userChallenge.getChallenge().getRewardXp() : 100;
        userXpService.awardXpAndPoints(user.getId(), xpReward, xpReward, "CHALLENGE", challengeId);

        return UserChallengeResponse.fromEntity(ChallengeResponse.fromEntity(userChallenge.getChallenge()), saved);
    }

    /**
     * Called automatically when a user logs an activity or footprint entry in the Tracking module.
     * Updates progress for active IN_PROGRESS challenges matching category or tracking type.
     */
    @Transactional
    public void processActivityUpdate(Long userId, String category, TrackingType trackingType, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<UserChallenge> activeUserChallenges = userChallengeRepository.findByUserId(userId)
                .stream()
                .filter(uc -> uc.getStatus() == UserChallengeStatus.IN_PROGRESS)
                .filter(uc -> {
                    Challenge c = uc.getChallenge();

                    // Ignore cancelled or expired challenges
                    if (c.isCancelled() || LocalDate.now().isAfter(c.getEndDate())) {
                        return false;
                    }

                    // Check Category Match (OVERALL matches all categories)
                    boolean categoryMatches = "OVERALL".equalsIgnoreCase(c.getCategory())
                            || c.getCategory().equalsIgnoreCase(category);

                    // Check Tracking Type Match for Activity-Based challenges
                    boolean trackingTypeMatches = c.getChallengeType() == ChallengeType.CARBON_REDUCTION
                            || (c.getTrackingType() != null && c.getTrackingType() == trackingType);

                    return categoryMatches && trackingTypeMatches;
                })
                .toList();

        for (UserChallenge uc : activeUserChallenges) {
            BigDecimal current = uc.getCurrentValue() != null ? uc.getCurrentValue() : BigDecimal.ZERO;
            BigDecimal updatedValue = current.add(amount);
            uc.setCurrentValue(updatedValue);

            BigDecimal target = uc.getChallenge().getTargetValue();
            if (target != null && target.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percentage = updatedValue
                        .multiply(new BigDecimal("100"))
                        .divide(target, 2, RoundingMode.HALF_UP);

                if (percentage.compareTo(new BigDecimal("100")) > 0) {
                    percentage = new BigDecimal("100");
                }
                uc.setCompletionPercentage(percentage);
            }

            userChallengeRepository.save(uc);
        }
    }

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }
}