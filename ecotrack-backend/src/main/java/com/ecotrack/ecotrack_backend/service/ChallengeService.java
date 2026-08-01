package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.ChallengeRequest;
import com.ecotrack.ecotrack_backend.dto.ChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.Challenge;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.ChallengeType;
import com.ecotrack.ecotrack_backend.entity.TrackingType;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidChallengeRequestException;
import com.ecotrack.ecotrack_backend.repository.ChallengeRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Admin-facing challenge management: create / edit / cancel / list.
 *
 * Deliberately does NOT include join, progress tracking, or activity
 * logging — that's UserChallenge territory on the public /api/challenges/*
 * side, a separate later piece of work per the Challenge Module Design doc.
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

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;

    public ChallengeService(ChallengeRepository challengeRepository, UserRepository userRepository) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
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
        CarbonCategory category = validateAndParseCategory(request);
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
        CarbonCategory category = validateAndParseCategory(request);
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

        // NOTE: bulk-updating non-terminal UserChallenge rows to CANCELLED
        // (and clawing back XP from any already-COMPLETED row) happens here
        // once the UserChallenge entity/repository exist. Nothing to cancel
        // yet since joining isn't built — soft-cancelling the Challenge row
        // itself is the complete, correct behavior for now.
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

    // Category must match CarbonCategory exactly (case-sensitive) — this is
    // what carbon_entries.category also uses, and the carbon-reduction
    // baseline calculation (Challenge Module Design doc §6) filters
    // carbon_entries by this exact string. A free-form category here would
    // silently produce a zero baseline for any mismatched casing/typo once
    // that join logic is built, with no error pointing back to the cause.
    private CarbonCategory validateAndParseCategory(ChallengeRequest request) {
        try {
            return CarbonCategory.valueOf(request.getCategory());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidChallengeRequestException(
                    "Category must be one of: TRANSPORTATION, ELECTRICITY, FOOD, WASTE, WATER, FUEL");
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