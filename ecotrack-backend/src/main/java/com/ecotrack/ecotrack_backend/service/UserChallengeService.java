package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.ChallengeResponse;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.*;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final XpTransactionRepository xpTransactionRepository;

    public UserChallengeService(
            ChallengeRepository challengeRepository,
            UserChallengeRepository userChallengeRepository,
            UserRepository userRepository,
            XpTransactionRepository xpTransactionRepository) {
        this.challengeRepository = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.userRepository = userRepository;
        this.xpTransactionRepository = xpTransactionRepository;
    }

    public List<UserChallengeResponse> getUserChallenges(String userEmail) {
        User user = findUserByEmailOrThrow(userEmail);

        // Fetch all active/non-cancelled challenges
        List<Challenge> challenges = challengeRepository.findAllByOrderByCreatedAtDesc();

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
    
        // Record XP transaction using your existing XpTransaction entity constructor
        int xpReward = userChallenge.getChallenge().getRewardXp() != null ? userChallenge.getChallenge().getRewardXp() : 100;
        XpTransaction xpTx = new XpTransaction(
                user.getId(),
                xpReward,
                "CHALLENGE",
                challengeId
        );
        xpTransactionRepository.save(xpTx);
    
        // Update user XP total
        int currentXp = user.getCurrentXp() != null ? user.getCurrentXp() : 0;
        user.setCurrentXp(currentXp + xpReward);
        userRepository.save(user);
    
        return UserChallengeResponse.fromEntity(ChallengeResponse.fromEntity(userChallenge.getChallenge()), saved);
    }

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }
}