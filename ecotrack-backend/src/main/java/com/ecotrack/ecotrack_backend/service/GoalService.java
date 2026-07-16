package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalRequest;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.entity.Goal;
import com.ecotrack.ecotrack_backend.entity.GoalProgress;
import com.ecotrack.ecotrack_backend.entity.GoalType;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.GoalRepository;
import com.ecotrack.ecotrack_backend.repository.GoalProgressRepository;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.exception.GoalAccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalProgressRepository goalProgressRepository;
    private final UserRepository userRepository;
    private final CarbonEntryRepository carbonEntryRepository;

    private static final Set<GoalType> AGGREGATE_ALL_CATEGORIES = EnumSet.of(GoalType.REDUCE_CARBON_EMISSIONS);
    private static final int DEFAULT_BASELINE_DAYS = 30;

    public GoalService(GoalRepository goalRepository,
            GoalProgressRepository goalProgressRepository,
            UserRepository userRepository,
            CarbonEntryRepository carbonEntryRepository) {
        this.goalRepository = goalRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.userRepository = userRepository;
        this.carbonEntryRepository = carbonEntryRepository;
    }

    // ── Create ──
    public GoalResponse createGoal(Long userId, GoalRequest request) {
        validateDateOrder(request);
        GoalType parsedType = parseGoalType(request.getGoalType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setGoalName(request.getGoalName());
        goal.setGoalType(parsedType.name());
        goal.setTargetValue(request.getTargetValue());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        goal.setRewardPoints(request.getRewardPoints() != null ? request.getRewardPoints() : 0);
        goal.setStatus("ACTIVE");

        int baselineDays = request.getBaselinePeriodDays() != null
                ? request.getBaselinePeriodDays()
                : DEFAULT_BASELINE_DAYS;
        LocalDate baselineStart = request.getStartDate().minusDays(baselineDays);
        LocalDate baselineEnd = request.getStartDate().minusDays(1);
        BigDecimal baselineValue = sumEmissions(userId,
                AGGREGATE_ALL_CATEGORIES.contains(parsedType) ? null : mapGoalTypeToCategory(parsedType),
                baselineStart, baselineEnd);
        goal.setBaselineValue(baselineValue);

        Goal saved = goalRepository.save(goal);

        // create an initial zeroed progress row so /progress never 404s right after
        // creation
        GoalProgress progress = new GoalProgress();
        progress.setGoal(saved);
        progress.setCurrentValue(BigDecimal.ZERO);
        progress.setCompletionPercentage(BigDecimal.ZERO);
        progress.setCompleted(false);
        goalProgressRepository.save(progress);

        return mapToResponse(saved);
    }

    // ── Update ──
    public GoalResponse updateGoal(Long userId, Long goalId, GoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to update this goal");
        }

        validateDateOrder(request);
        GoalType parsedType = parseGoalType(request.getGoalType());

        goal.setGoalName(request.getGoalName());
        goal.setGoalType(parsedType.name());
        goal.setTargetValue(request.getTargetValue());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        if (request.getRewardPoints() != null) {
            goal.setRewardPoints(request.getRewardPoints());
        }

        Goal saved = goalRepository.save(goal);
        return mapToResponse(saved);
    }

    // ── Read ──
    public List<GoalResponse> getGoalsForUser(Long userId) {
        return goalRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<GoalResponse> getGoalsForUserByStatus(Long userId, String status) {
        return goalRepository.findByUserIdAndStatus(userId, status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public GoalResponse getGoalById(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to view this goal");
        }

        return mapToResponse(goal);
    }

    // ── Progress (lazy — only computed when this is called, i.e. on GET) ──
    public GoalProgressResponse calculateProgress(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to view this goal");
        }

        GoalProgress progress = goalProgressRepository.findByGoalGoalId(goalId)
                .orElseThrow(() -> new IllegalArgumentException("No progress record for goal: " + goalId));

        GoalType type = parseGoalType(goal.getGoalType());
        CarbonCategory category = AGGREGATE_ALL_CATEGORIES.contains(type) ? null : mapGoalTypeToCategory(type);

        LocalDate periodEnd = LocalDate.now().isBefore(goal.getEndDate()) ? LocalDate.now() : goal.getEndDate();

        List<CarbonEntry> currentEntries = (category != null)
                ? carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(userId, category,
                        goal.getStartDate(), periodEnd)
                : carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, goal.getStartDate(), periodEnd);

        BigDecimal currentValue = currentEntries.stream()
                .map(CarbonEntry::getCarbonEmission)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completionPercentage;
        boolean completed;

        if (currentEntries.isEmpty()) {
            completionPercentage = BigDecimal.ZERO;
            completed = false;
        } else {
            BigDecimal reduction = goal.getBaselineValue().subtract(currentValue);
            if (reduction.compareTo(BigDecimal.ZERO) < 0) {
                reduction = BigDecimal.ZERO;
            }
            if (goal.getTargetValue().compareTo(BigDecimal.ZERO) == 0) {
                completionPercentage = BigDecimal.ZERO;
            } else {
                completionPercentage = reduction.divide(goal.getTargetValue(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                if (completionPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                    completionPercentage = BigDecimal.valueOf(100);
                }
            }
            completed = completionPercentage.compareTo(BigDecimal.valueOf(100)) >= 0;
        }

        progress.setCurrentValue(currentValue);
        progress.setCompletionPercentage(completionPercentage);
        progress.setCompleted(completed);
        goalProgressRepository.save(progress);

        if (completed && !"COMPLETED".equals(goal.getStatus())) {
            goal.setStatus("COMPLETED");
            goalRepository.save(goal);
        }

        return buildProgressResponse(goal, progress);
    }

    // ── Emission helpers ──
    private BigDecimal sumEmissions(Long userId, CarbonCategory category, LocalDate from, LocalDate to) {
        List<CarbonEntry> entries = (category != null)
                ? carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(userId, category, from, to)
                : carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);

        return entries.stream()
                .map(CarbonEntry::getCarbonEmission)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CarbonCategory mapGoalTypeToCategory(GoalType goalType) {
        return switch (goalType) {
            case REDUCE_ELECTRICITY_CONSUMPTION -> CarbonCategory.ELECTRICITY;
            case REDUCE_WATER_USAGE -> CarbonCategory.WATER;
            case REDUCE_TRANSPORTATION_EMISSIONS -> CarbonCategory.TRANSPORTATION;
            case REDUCE_FUEL_CONSUMPTION -> CarbonCategory.FUEL;
            case REDUCE_FOOD_EMISSIONS -> CarbonCategory.FOOD;
            case REDUCE_WASTE_GENERATION -> CarbonCategory.WASTE;
            case REDUCE_CARBON_EMISSIONS -> null;
        };
    }

    // ── Validation helpers ──
    private void validateDateOrder(GoalRequest request) {
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }

    private GoalType parseGoalType(String rawType) {
        try {
            return GoalType.valueOf(rawType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid goalType: " + rawType);
        }
    }

    // ── Mapping ──
    private GoalResponse mapToResponse(Goal goal) {
        return new GoalResponse(
                goal.getGoalId(),
                goal.getGoalName(),
                goal.getGoalType(),
                goal.getTargetValue(),
                goal.getBaselineValue(),
                goal.getRewardPoints(),
                goal.getStatus(),
                goal.getStartDate(),
                goal.getEndDate());
    }

    private GoalProgressResponse buildProgressResponse(Goal goal, GoalProgress progress) {
        return new GoalProgressResponse(
                goal.getGoalId(),
                progress.getCurrentValue(),
                goal.getTargetValue(),
                goal.getBaselineValue(),
                progress.getCompletionPercentage(),
                progress.isCompleted(),
                progress.getLastUpdated());
    }
}