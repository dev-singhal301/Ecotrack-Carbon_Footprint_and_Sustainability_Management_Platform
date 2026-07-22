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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public GoalResponse createGoal(Long userId, GoalRequest request) {
        // Automatically calculate start and end dates based on duration input
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(request.getDurationDays());

        GoalType parsedType = parseGoalType(request.getGoalType());
        
        validateNoOverlap(userId, parsedType.name(), startDate, endDate, null);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Find the absolute last entry recorded by this user right now to establish a baseline token anchor
        List<CarbonEntry> allEntries = carbonEntryRepository.findByUserId(userId);
        Long maxEntryId = allEntries.stream()
                .map(CarbonEntry::getEntryId)
                .max(Long::compare)
                .orElse(0L);

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setGoalName(request.getGoalName());
        goal.setGoalType(parsedType.name());
        goal.setTargetValue(request.getTargetValue());
        
        // Save computed dates to database entities
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        
        goal.setRewardPoints(request.getRewardPoints() != null ? request.getRewardPoints() : 0);
        goal.setXpReward(request.getRewardPoints() != null ? request.getRewardPoints() : 100);
        goal.setStatus("ACTIVE");
        
        // Setup isolation boundary: tracking ignores anything logged up to this entry ID
        goal.setStartEntryId(maxEntryId);

        // Safe fallback since baselinePeriodDays was removed from GoalRequest DTO
        int baselineDays = DEFAULT_BASELINE_DAYS;
        LocalDate baselineStart = startDate.minusDays(baselineDays);
        LocalDate baselineEnd = startDate.minusDays(1);
        BigDecimal baselineValue = sumEmissions(userId,
                AGGREGATE_ALL_CATEGORIES.contains(parsedType) ? null : mapGoalTypeToCategory(parsedType),
                baselineStart, baselineEnd);
        goal.setBaselineValue(baselineValue);

        Goal saved = goalRepository.save(goal);

        GoalProgress progress = new GoalProgress();
        progress.setGoal(saved);
        progress.setCurrentValue(BigDecimal.ZERO);
        progress.setCompletionPercentage(BigDecimal.ZERO);
        progress.setCompleted(false);
        goalProgressRepository.save(progress);

        return mapToResponse(saved);
    }

    // ── Update ──
    @Transactional
    public GoalResponse updateGoal(Long userId, Long goalId, GoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to update this goal");
        }

        // recalculate based on updated duration input relative to initial or current execution
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(request.getDurationDays());
        
        GoalType parsedType = parseGoalType(request.getGoalType());
        
        validateNoOverlap(userId, parsedType.name(), startDate, endDate, goalId);

        // Re-anchor the baseline marker if the goal configuration updates
        List<CarbonEntry> allEntries = carbonEntryRepository.findByUserId(userId);
        Long maxEntryId = allEntries.stream()
                .map(CarbonEntry::getEntryId)
                .max(Long::compare)
                .orElse(0L);

        goal.setGoalName(request.getGoalName());
        goal.setGoalType(parsedType.name());
        goal.setTargetValue(request.getTargetValue());
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setStartEntryId(maxEntryId);
        if (request.getRewardPoints() != null) {
            goal.setRewardPoints(request.getRewardPoints());
            goal.setXpReward(request.getRewardPoints());
        }

        Goal saved = goalRepository.save(goal);
        return mapToResponse(saved);
    }

    // ── Complete Goal & Award XP ──
    @Transactional
    public void completeGoal(Long userId, Long goalId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to complete this goal");
        }

        if ("COMPLETED".equalsIgnoreCase(goal.getStatus())) {
            throw new IllegalStateException("Goal is already completed");
        }

        // 1. Calculate and award XP and Reward Points
        int xpToAward = (goal.getXpReward() != null) ? goal.getXpReward() : 
                        (goal.getRewardPoints() != null ? goal.getRewardPoints() : 0);
        int pointsToAward = (goal.getRewardPoints() != null) ? goal.getRewardPoints() : 0;

        int currentXp = (user.getCurrentXp() != null) ? user.getCurrentXp() : 0;
        int currentPoints = (user.getRewardPoints() != null) ? user.getRewardPoints() : 0;

        user.setCurrentXp(currentXp + xpToAward);
        user.setRewardPoints(currentPoints + pointsToAward);
        userRepository.save(user);

        // 2. Mark goal status as COMPLETED
        goal.setStatus("COMPLETED");
        goalRepository.save(goal);

        // 3. Update associated progress record if present
        goalProgressRepository.findByGoalGoalId(goalId).ifPresent(progress -> {
            progress.setCompleted(true);
            progress.setCompletedDate(LocalDate.now());
            progress.setCompletionPercentage(BigDecimal.valueOf(100));
            goalProgressRepository.save(progress);
        });
    }

    // ── Delete ──
    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to delete this goal");
        }

        goalProgressRepository.findByGoalGoalId(goalId).ifPresent(goalProgressRepository::delete);
        goalRepository.delete(goal);
    }

    // ── Read ──
    public List<GoalResponse> getGoalsForUser(Long userId) {
        List<Goal> goals = goalRepository.findByUserId(userId);
        goals.forEach(this::checkAndHandleExpiration);
        return goals.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<GoalResponse> getGoalsForUserByStatus(Long userId, String status) {
        if ("ACTIVE".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
            List<Goal> allUserGoals = goalRepository.findByUserId(userId);
            allUserGoals.forEach(this::checkAndHandleExpiration);
            return allUserGoals.stream()
                    .filter(g -> g.getStatus().equalsIgnoreCase(status))
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        
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

        checkAndHandleExpiration(goal);
        return mapToResponse(goal);
    }

    // ── Progress Evaluation ──
    @Transactional
    public GoalProgressResponse calculateProgress(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));

        if (!goal.getUser().getId().equals(userId)) {
            throw new GoalAccessDeniedException("You do not have permission to view this goal");
        }

        checkAndHandleExpiration(goal);

        GoalProgress progress = goalProgressRepository.findByGoalGoalId(goalId)
                .orElseThrow(() -> new IllegalArgumentException("No progress record for goal: " + goalId));

        GoalType type = parseGoalType(goal.getGoalType());
        CarbonCategory category = AGGREGATE_ALL_CATEGORIES.contains(type) ? null : mapGoalTypeToCategory(type);

        LocalDate periodEnd = LocalDate.now().isBefore(goal.getEndDate()) ? LocalDate.now() : goal.getEndDate();

        // 1. Fetch raw timeframe entries (Keeps global tracking dashboard metrics safe)
        List<CarbonEntry> timeFrameEntries = (category != null)
                ? carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(userId, category,
                        goal.getStartDate(), periodEnd)
                : carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, goal.getStartDate(), periodEnd);

        Long startingAnchorId = goal.getStartEntryId() != null ? goal.getStartEntryId() : 0L;

        // 2. Isolate values: Only sum up metrics that were created AFTER the goal initialization token
        BigDecimal currentValue = timeFrameEntries.stream()
                .filter(entry -> entry.getEntryId() > startingAnchorId)
                .map(CarbonEntry::getCarbonEmission)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completionPercentage = BigDecimal.ZERO;
        boolean completed = false;

        // Percentage tracking reflects how much of the target allowance has been consumed
        if (goal.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
            completionPercentage = currentValue.divide(goal.getTargetValue(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (completionPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                completionPercentage = BigDecimal.valueOf(100);
            }
        }

        progress.setCurrentValue(currentValue);
        progress.setCompletionPercentage(completionPercentage);
        
        // ── AUTOMATED TRIGGER EXECUTION: Handle Ceiling Limit Evaluation ──
        if ("ACTIVE".equals(goal.getStatus())) {
            // Breached if the tracking consumption goes above the absolute budget allowance cap
            if (currentValue.compareTo(goal.getTargetValue()) > 0) {
                goal.setStatus("EXPIRED");
                completed = false;
            } 
            // Successfully completed if deadline passes cleanly while staying inside limits
            else if (LocalDate.now().isAfter(goal.getEndDate())) {
                // Trigger goal completion reward flow automatically
                completeGoal(userId, goalId);
                return buildProgressResponse(goal, progress);
            }
        } else if ("COMPLETED".equals(goal.getStatus())) {
            completed = true;
        }

        progress.setCompleted(completed);
        goalProgressRepository.save(progress);
        goalRepository.save(goal);

        return buildProgressResponse(goal, progress);
    }

    // ── Expiration Engine Helper ──
    private void checkAndHandleExpiration(Goal goal) {
        if ("ACTIVE".equals(goal.getStatus()) && LocalDate.now().isAfter(goal.getEndDate())) {
            // Trigger automatic completion & XP awarding if end date passed without breaching target budget
            try {
                completeGoal(goal.getUser().getId(), goal.getGoalId());
            } catch (Exception e) {
                goal.setStatus("EXPIRED");
                goalRepository.save(goal);
            }
        }
    }

    // ── Overlap Checker Helper ──
    private void validateNoOverlap(Long userId, String goalType, LocalDate start, LocalDate end, Long excludeGoalId) {
        List<Goal> conflictingGoals = goalRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(g -> !g.getGoalId().equals(excludeGoalId))
                .filter(g -> g.getGoalType().equalsIgnoreCase(goalType))
                .peek(g -> {
                    // Force a progress check calculation to catch any stealth breaches before running structural constraint checks
                    try {
                        this.calculateProgress(userId, g.getGoalId());
                    } catch (Exception ignored) {}
                })
                .filter(g -> g.getStatus().equalsIgnoreCase("ACTIVE")) // Verify it is still active post-evaluation
                .filter(g -> !(end.isBefore(g.getStartDate()) || start.isAfter(g.getEndDate())))
                .toList();

        if (!conflictingGoals.isEmpty()) {
            throw new IllegalArgumentException("You already have an active " + goalType + 
                    " goal set up within this timeframe (" + conflictingGoals.get(0).getStartDate() + " to " + conflictingGoals.get(0).getEndDate() + ").");
        }
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