package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.ChallengeRequest;
import com.ecotrack.ecotrack_backend.dto.ChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.Challenge;
import com.ecotrack.ecotrack_backend.entity.ChallengeCategory;
import com.ecotrack.ecotrack_backend.entity.ChallengeType;
import com.ecotrack.ecotrack_backend.entity.TrackingType;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.UserChallenge;
import com.ecotrack.ecotrack_backend.entity.UserChallengeStatus;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidChallengeRequestException;
import com.ecotrack.ecotrack_backend.repository.ChallengeRepository;
import com.ecotrack.ecotrack_backend.repository.UserChallengeRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Admin-facing challenge management: create / edit / cancel / list.
 *
 * Deliberately does NOT include join, progress tracking, or activity
 * logging — that's UserChallengeService's territory (see that class). The
 * one exception is cancellation: cancelChallenge() reaches into
 * UserChallengeRepository directly to cascade CANCELLED status (and XP
 * clawback) to every participant, since that's fundamentally an admin
 * action with side effects on participants, not a user-initiated one.
 *
 * Exception convention (matches AdminController / AdminService, which
 * already established IllegalArgumentException = "not found" -> 404 for
 * user management): validation failures here throw
 * InvalidChallengeRequestException -> 400, NOT IllegalArgumentException,
 * so they don't collide with or get mislabeled by that existing mapping.
 * "Not found" cases throw ChallengeNotFoundException -> 404, same shape
 * as the user-management convention, just its own type.
 */
@Service
public class ChallengeService {

    private static final int MIN_DURATION_DAYS = 7;

    // Non-terminal statuses a participant can be in when a challenge is cancelled.
    private static final Set<UserChallengeStatus> CANCELLABLE_STATUSES =
            EnumSet.of(UserChallengeStatus.JOINED, UserChallengeStatus.IN_PROGRESS);

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LeaderboardService leaderboardService;

    public ChallengeService(ChallengeRepository challengeRepository,
                             UserRepository userRepository,
                             UserChallengeRepository userChallengeRepository,
                             ApplicationEventPublisher eventPublisher,
                             LeaderboardService leaderboardService) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.eventPublisher = eventPublisher;
        this.leaderboardService = leaderboardService;
    }

    public List<ChallengeResponse> getAllChallenges() {
        return challengeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ChallengeResponse::fromEntity)
                .toList();
    }

    public ChallengeResponse getChallengeById(Long challengeId) {
        return ChallengeResponse.fromEntity(findChallengeOrThrow(challengeId));
    }

    public ChallengeResponse createChallenge(String adminEmail, ChallengeRequest request) {
        ChallengeType challengeType = validateAndParseType(request);
        TrackingType trackingType = validateAndParseTrackingType(request, challengeType);
        ChallengeCategory category = validateAndParseCategory(request);
        validateTargetValue(request, challengeType);
        validateDates(request.getStartDate(), request.getEndDate());

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new InvalidChallengeRequestException("Start date cannot be in the past");
        }

        // IllegalArgumentException here, not InvalidChallengeRequestException,
        // to match AdminService's existing "not found" -> 404 convention —
        // this is genuinely a not-found case (JWT valid but the underlying
        // admin User row is gone), not a bad request from the client.
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminEmail));

        Challenge challenge = new Challenge();
        challenge.setTitle(request.getTitle());
        challenge.setDescription(request.getDescription());
        challenge.setCategory(category.name());
        challenge.setChallengeType(challengeType);
        challenge.setTrackingType(trackingType);
        challenge.setTargetValue(request.getTargetValue());
        challenge.setUnit(request.getUnit());
        challenge.setRewardXp(request.getRewardXp() != null ? request.getRewardXp() : 100);
        challenge.setBadgeImageUrl(request.getBadgeImageUrl());
        challenge.setCardImageUrl(request.getCardImageUrl());
        challenge.setStartDate(request.getStartDate());
        challenge.setEndDate(request.getEndDate());
        challenge.setCreatedBy(admin);

        Challenge saved = challengeRepository.save(challenge);
        return ChallengeResponse.fromEntity(saved);
    }

    public ChallengeResponse updateChallenge(Long challengeId, ChallengeRequest request) {
        Challenge challenge = findChallengeOrThrow(challengeId);

        // Admin edit lock: any field is editable while UPCOMING; once
        // today >= start_date, the definition is frozen. This protects
        // users who've already joined and have a frozen baseline/target.
        if (!LocalDate.now().isBefore(challenge.getStartDate())) {
            throw new IllegalStateException(
                    "This challenge can no longer be edited — it has already started or ended");
        }
        if (challenge.isCancelled()) {
            throw new IllegalStateException("This challenge has been cancelled and cannot be edited");
        }

        ChallengeType challengeType = validateAndParseType(request);
        TrackingType trackingType = validateAndParseTrackingType(request, challengeType);
        ChallengeCategory category = validateAndParseCategory(request);
        validateTargetValue(request, challengeType);
        validateDates(request.getStartDate(), request.getEndDate());

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new InvalidChallengeRequestException("Start date cannot be in the past");
        }

        challenge.setTitle(request.getTitle());
        challenge.setDescription(request.getDescription());
        challenge.setCategory(category.name());
        challenge.setChallengeType(challengeType);
        challenge.setTrackingType(trackingType);
        challenge.setTargetValue(request.getTargetValue());
        challenge.setUnit(request.getUnit());
        if (request.getRewardXp() != null) {
            challenge.setRewardXp(request.getRewardXp());
        }
        challenge.setBadgeImageUrl(request.getBadgeImageUrl());
        challenge.setCardImageUrl(request.getCardImageUrl());
        challenge.setStartDate(request.getStartDate());
        challenge.setEndDate(request.getEndDate());

        Challenge saved = challengeRepository.save(challenge);
        return ChallengeResponse.fromEntity(saved);
    }

    @Transactional
    public void cancelChallenge(Long challengeId) {
        Challenge challenge = findChallengeOrThrow(challengeId);

        if (challenge.isCancelled()) {
            throw new IllegalStateException("This challenge has already been cancelled");
        }
        // Cancellation allowed while UPCOMING or ACTIVE, but not once
        // today > end_date (challenge already ENDED).
        if (LocalDate.now().isAfter(challenge.getEndDate())) {
            throw new IllegalStateException("This challenge has already ended and cannot be cancelled");
        }

        challenge.setCancelled(true);
        challenge.setCancelledAt(LocalDateTime.now());
        challengeRepository.save(challenge);

        cascadeCancellationToParticipants(challenge);
    }

    /**
     * Design doc §5 / Workflow doc §12: cancelling bulk-updates every
     * non-terminal participant row to CANCELLED, and any row already
     * COMPLETED is also reverted to CANCELLED with its previously-awarded
     * XP deducted (clamped at 0 — never negative). No participant is ever
     * left showing as "failed" because of an admin cancellation, and a
     * single ChallengeCancelledEvent carries every affected user id so
     * Person C's Notification module can inform them.
     *
     * The clawback goes through LeaderboardService.clawbackXp rather than
     * touching User.currentXp directly, so a matching negative XpTransaction
     * (source "CHALLENGE_CANCELLED") lands in the ledger alongside the
     * decrement — otherwise monthly/previous-month leaderboards and XP
     * history, which sum xp_transactions by date range rather than reading
     * currentXp, would never reflect the clawback and could show a user's
     * all-time total as lower than their monthly-history sum implies.
     */
    private void cascadeCancellationToParticipants(Challenge challenge) {
        List<UserChallenge> participants = userChallengeRepository.findByChallengeId(challenge.getChallengeId())
                .stream()
                .filter(uc -> CANCELLABLE_STATUSES.contains(uc.getStatus())
                        || uc.getStatus() == UserChallengeStatus.COMPLETED)
                .toList();

        List<Long> affectedUserIds = new java.util.ArrayList<>();

        for (UserChallenge uc : participants) {
            if (uc.getStatus() == UserChallengeStatus.COMPLETED) {
                int rewardXp = challenge.getRewardXp() != null ? challenge.getRewardXp() : 0;
                if (rewardXp > 0) {
                    leaderboardService.clawbackXp(
                            uc.getUser().getId(), rewardXp, "CHALLENGE_CANCELLED", challenge.getChallengeId());
                }
            }
            uc.setStatus(UserChallengeStatus.CANCELLED);
            userChallengeRepository.save(uc);
            affectedUserIds.add(uc.getUser().getId());
        }

        if (!affectedUserIds.isEmpty()) {
            eventPublisher.publishEvent(new ChallengeCancelledEvent(this, challenge.getChallengeId(), affectedUserIds));
        }
    }

    // ---- validation helpers ----

    private ChallengeType validateAndParseType(ChallengeRequest request) {
        try {
            return ChallengeType.valueOf(request.getChallengeType());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidChallengeRequestException(
                    "Challenge type must be CARBON_REDUCTION or ACTIVITY_BASED");
        }
    }

    // Challenges have their own category set (ChallengeCategory), separate
    // from the shared CarbonCategory that Goals/CarbonEntry/Recommendations
    // use — it includes OVERALL (sum across every category) in addition to
    // the six specific ones. Everything except OVERALL still shares the
    // exact same spelling as CarbonCategory, so a future carbon-reduction
    // baseline calculation (Design doc §6) can filter carbon_entries by
    // this string directly for a specific-category challenge.
    private ChallengeCategory validateAndParseCategory(ChallengeRequest request) {
        try {
            return ChallengeCategory.valueOf(request.getCategory());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidChallengeRequestException(
                    "Category must be one of: OVERALL, TRANSPORTATION, ELECTRICITY, FOOD, WASTE, WATER, FUEL");
        }
    }

    private TrackingType validateAndParseTrackingType(ChallengeRequest request, ChallengeType challengeType) {
        String rawTrackingType = request.getTrackingType();

        if (challengeType == ChallengeType.CARBON_REDUCTION) {
            if (rawTrackingType != null && !rawTrackingType.isBlank()) {
                throw new InvalidChallengeRequestException("Carbon-reduction challenges must not set a tracking type");
            }
            return null;
        }

        // ACTIVITY_BASED requires a tracking type
        if (rawTrackingType == null || rawTrackingType.isBlank()) {
            throw new InvalidChallengeRequestException("Activity-based challenges require a tracking type");
        }
        try {
            return TrackingType.valueOf(rawTrackingType);
        } catch (IllegalArgumentException ex) {
            throw new InvalidChallengeRequestException("Tracking type must be one of: COUNT, DISTANCE, DURATION, BOOLEAN");
        }
    }

    // A CARBON_REDUCTION target is a percentage (see effective_target formula
    // in the Design doc §6: baseline * (1 - target/100)) — anything above
    // 100 is nonsensical (can't reduce more than 100% of a baseline) and
    // @Positive alone doesn't catch it. ACTIVITY_BASED targets are raw
    // counts/amounts (trees, km, minutes) with no such ceiling.
    private void validateTargetValue(ChallengeRequest request, ChallengeType challengeType) {
        if (challengeType == ChallengeType.CARBON_REDUCTION
                && request.getTargetValue().compareTo(new BigDecimal("100")) > 0) {
            throw new InvalidChallengeRequestException(
                    "Carbon-reduction target must be a percentage between 0 and 100");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new InvalidChallengeRequestException("Start date must be before end date");
        }
        long durationDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (durationDays < MIN_DURATION_DAYS) {
            throw new InvalidChallengeRequestException("Challenge must run for at least " + MIN_DURATION_DAYS + " days");
        }
    }

    private Challenge findChallengeOrThrow(Long challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Challenge not found: " + challengeId));
    }
}