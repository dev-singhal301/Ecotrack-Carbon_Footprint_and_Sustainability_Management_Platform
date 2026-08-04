package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.dto.CarbonEntryResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.EmissionFactor;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.TrackingType;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.CarbonEntryNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidCategoryException;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.EmissionFactorRepository;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CarbonEntryService {

    private static final int LOG_ENTRY_XP = 10; // XP awarded on first daily activity

    private final CarbonEntryRepository carbonEntryRepository;
    private final EmissionFactorRepository emissionFactorRepository;
    private final GoalRepository goalRepository;
    private final GoalService goalService;
    private final UserXpService userXpService;
    private final UserChallengeService userChallengeService;

    public CarbonEntryService(CarbonEntryRepository carbonEntryRepository,
                               EmissionFactorRepository emissionFactorRepository,
                               GoalRepository goalRepository,
                               GoalService goalService,
                               UserXpService userXpService,
                               UserChallengeService userChallengeService) {
        this.carbonEntryRepository = carbonEntryRepository;
        this.emissionFactorRepository = emissionFactorRepository;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
        this.userXpService = userXpService;
        this.userChallengeService = userChallengeService;
    }

    private CarbonCategory parseCategory(String rawCategory) {
        try {
            return CarbonCategory.valueOf(rawCategory.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCategoryException(
                    "Invalid category: " + rawCategory + ". Must be one of: TRANSPORTATION, ELECTRICITY, FOOD, WASTE, WATER, FUEL");
        }
    }

    @Transactional
    public CarbonEntryResponse createEntry(CarbonEntryRequest request, User user) {
        CarbonCategory category = parseCategory(request.getCategory());

        EmissionFactor factor = emissionFactorRepository.findByCategory(category)
                .orElseThrow(() -> new InvalidCategoryException(
                        "No emission factor configured for category: " + category));

        CarbonEntry entry = new CarbonEntry();
        entry.setUser(user);
        entry.setEmissionFactor(factor);
        entry.setCategory(category);
        entry.setAmount(request.getAmount());
        entry.setUnit(factor.getUnit());
        entry.setActivityDate(request.getActivityDate());
        entry.setCarbonEmission(request.getAmount().multiply(factor.getFactorValue()));

        CarbonEntry saved = carbonEntryRepository.save(entry);

        // 1. Anti-Spam Gamification Check: Award XP only if this is the first entry logged today
        LocalDate today = LocalDate.now();
        boolean isFirstLogToday = carbonEntryRepository
                .findByUserIdAndActivityDateBetween(user.getId(), today, today)
                .size() <= 1; // <= 1 because 'saved' entry was just persisted above

        if (isFirstLogToday) {
            // Delegate to UserXpService: Creates XpTransaction AND updates user level + total XP
            userXpService.awardXpAndPoints(user.getId(), LOG_ENTRY_XP, 0, "DAILY_LOG", saved.getEntryId());
        }

        // 2. Refresh progress for active goals affected by this new entry
        triggerActiveGoalRecalculation(user.getId());

        // 3. Automatically update progress for active user challenges matching category & tracking type
        triggerActiveChallengeProgress(user.getId(), category.name(), factor.getUnit(), request.getAmount());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CarbonEntryResponse> getHistory(Long userId, String rawCategory, LocalDate from, LocalDate to) {
        List<CarbonEntry> entries;

        if (rawCategory != null && from != null && to != null) {
            entries = carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                    userId, parseCategory(rawCategory), from, to);
        } else if (rawCategory != null) {
            entries = carbonEntryRepository.findByUserIdAndCategory(userId, parseCategory(rawCategory));
        } else if (from != null && to != null) {
            entries = carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);
        } else {
            entries = carbonEntryRepository.findByUserId(userId);
        }

        return entries.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CarbonEntryResponse getEntryById(Long userId, Long entryId) {
        CarbonEntry entry = carbonEntryRepository.findById(entryId)
                .filter(e -> e.getUser().getId().equals(userId))
                .orElseThrow(() -> new CarbonEntryNotFoundException("Carbon entry not found: " + entryId));
        return toResponse(entry);
    }

    @Transactional
    public void deleteEntry(Long userId, Long entryId) {
        CarbonEntry entry = carbonEntryRepository.findById(entryId)
                .filter(e -> e.getUser().getId().equals(userId))
                .orElseThrow(() -> new CarbonEntryNotFoundException("Carbon entry not found: " + entryId));

        carbonEntryRepository.delete(entry);

        // Recalculate goal progress to accurately reflect the removal of emissions
        triggerActiveGoalRecalculation(userId);
    }

    // ── Gamification & Challenge Helpers ──

    private void triggerActiveGoalRecalculation(Long userId) {
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, "ACTIVE");
        for (Goal goal : activeGoals) {
            try {
                goalService.calculateProgress(userId, goal.getGoalId());
            } catch (Exception ignored) {
                // Silently ignore individual goal evaluation errors to prevent blocking entry creation
            }
        }
    }

    private void triggerActiveChallengeProgress(Long userId, String category, String unit, BigDecimal amount) {
        try {
            TrackingType trackingType = determineTrackingType(unit);
            userChallengeService.processActivityUpdate(userId, category, trackingType, amount);
        } catch (Exception ignored) {
            // Silently ignore challenge evaluation errors to prevent blocking entry creation
        }
    }

    private TrackingType determineTrackingType(String unit) {
        if (unit == null) return TrackingType.COUNT;
        String u = unit.toLowerCase();
        if (u.contains("km") || u.contains("mile") || u.contains("m")) return TrackingType.DISTANCE;
        if (u.contains("hour") || u.contains("hr") || u.contains("min")) return TrackingType.DURATION;
        return TrackingType.COUNT;
    }

    private CarbonEntryResponse toResponse(CarbonEntry entry) {
        return new CarbonEntryResponse(
                entry.getEntryId(),
                entry.getCategory().name(),
                entry.getAmount(),
                entry.getUnit(),
                entry.getCarbonEmission(),
                entry.getActivityDate()
        );
    }
}