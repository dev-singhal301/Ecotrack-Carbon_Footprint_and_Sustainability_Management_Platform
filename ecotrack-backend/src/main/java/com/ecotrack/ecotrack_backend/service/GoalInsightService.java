package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.GoalInsightDetail;
import com.ecotrack.ecotrack_backend.dto.GoalInsightResponse;
import com.ecotrack.ecotrack_backend.dto.GoalInsightsBatchResponse;
import com.ecotrack.ecotrack_backend.dto.GoalProgressResponse;
import com.ecotrack.ecotrack_backend.dto.GoalResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates Goal Insights: reads goals via Dev's GoalService (read-only,
 * same cross-dependency pattern as CarbonEntryRepository in Milestone 2),
 * builds the rule-based fallback for each visible goal, then asks
 * AiTipService to try to upgrade all of them in a single batched Gemini call.
 *
 * No persistence table here (unlike Recommendations) — goal progress is
 * always live, so this recomputes fresh on every call. The frontend
 * GoalInsightService is what prevents redundant calls (fire-once ensureLoaded,
 * explicit refresh), same discipline as Recommendations' persisted table,
 * just enforced client-side since there's nothing to store server-side.
 */
@Service
public class GoalInsightService {

    private static final DateTimeFormatter END_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final int MAX_ENDED_GOALS = 7;

    private final GoalService goalService;
    private final GoalInsightRuleEngine ruleEngine;
    private final AiTipService aiTipService;

    public GoalInsightService(GoalService goalService, GoalInsightRuleEngine ruleEngine, AiTipService aiTipService) {
        this.goalService = goalService;
        this.ruleEngine = ruleEngine;
        this.aiTipService = aiTipService;
    }

    public GoalInsightsBatchResponse getInsights(Long userId) {
        List<GoalResponse> allGoals = goalService.getGoalsForUser(userId);

        List<GoalResponse> active = allGoals.stream()
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .toList();

        List<GoalResponse> ended = allGoals.stream()
                .filter(g -> "COMPLETED".equalsIgnoreCase(g.getStatus()) || "EXPIRED".equalsIgnoreCase(g.getStatus()))
                .sorted(Comparator.comparing(GoalResponse::getEndDate).reversed())
                .limit(MAX_ENDED_GOALS)
                .toList();

        List<GoalResponse> visible = new ArrayList<>(active);
        visible.addAll(ended);

        if (visible.isEmpty()) {
            return new GoalInsightsBatchResponse(List.of());
        }

        record Prepared(GoalResponse goal, GoalProgressResponse progress, AiTipService.GoalInsightCandidate candidate) {}

        List<AiTipService.GoalInsightCandidate> candidates = new ArrayList<>();
        List<Prepared> prepared = new ArrayList<>();

        for (GoalResponse goal : visible) {
            GoalProgressResponse progress;
            try {
                progress = goalService.calculateProgress(userId, goal.getGoalId());
            } catch (Exception e) {
                // Skip a goal whose progress can't be computed rather than
                // failing the whole batch for the user's other goals.
                continue;
            }

            GoalInsightRuleEngine.Status status = ruleEngine.getStatus(
                    goal.getStartDate(), goal.getEndDate(),
                    progress.getCompletionPercentage(),
                    progress.getCurrentValue(), progress.getTargetValue(),
                    progress.isCompleted());

            String currentDisplay = formatValue(progress.getCurrentValue());
            String targetDisplay = formatValue(progress.getTargetValue());
            String endDateDisplay = goal.getEndDate().format(END_DATE_FORMAT);
            long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), goal.getEndDate()));

            GoalInsightDetail fallback = ruleEngine.getStructuredInsight(
                    goal.getGoalType(), status, currentDisplay, targetDisplay, endDateDisplay, daysRemaining);

            AiTipService.GoalInsightCandidate candidate = new AiTipService.GoalInsightCandidate(
                    goal.getGoalId(), goal.getGoalName(), goal.getGoalType(), status,
                    currentDisplay, targetDisplay, endDateDisplay, daysRemaining, fallback);

            candidates.add(candidate);
            prepared.add(new Prepared(goal, progress, candidate));
        }

        List<GoalInsightDetail> details = aiTipService.generateGoalInsights(candidates);

        List<GoalInsightResponse> insights = new ArrayList<>();
        for (int i = 0; i < prepared.size(); i++) {
            Prepared p = prepared.get(i);
            insights.add(new GoalInsightResponse(
                    p.goal().getGoalId(),
                    p.goal().getGoalName(),
                    p.goal().getGoalType(),
                    p.progress().getCurrentValue(),
                    p.progress().getTargetValue(),
                    p.progress().getCompletionPercentage(),
                    p.progress().isCompleted(),
                    p.goal().getEndDate().toString(),
                    details.get(i)
            ));
        }

        return new GoalInsightsBatchResponse(insights);
    }

    private String formatValue(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}