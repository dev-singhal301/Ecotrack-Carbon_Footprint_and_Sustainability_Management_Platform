package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.AiRecommendationDetail;
import com.ecotrack.ecotrack_backend.dto.CategoryBreakdownDto;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import com.ecotrack.ecotrack_backend.dto.GoalInsightDetail;
import com.ecotrack.ecotrack_backend.service.GoalInsightRuleEngine.Status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batched AI reasoning layer. One Gemini call analyzes the user's full
 * emission profile and returns structured recommendations for ALL top-3
 * categories at once (not one call per category — that's what was causing
 * ~90s load times).
 *
 * GUARANTEE: generateBatch(...) NEVER throws and ALWAYS returns exactly
 * one AiRecommendationDetail per requested category, in the same order.
 * On ANY failure (disabled, network, timeout, malformed/incomplete JSON,
 * category mismatch) it returns the pre-built rule-based fallbacks
 * unchanged, with source = "RULE_BASED".
 */
@Service
public class AiTipService {

    private static final Logger log = LoggerFactory.getLogger(AiTipService.class);

    private final Client client;
    private final String model;
    private final boolean enabled;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AiTipService(@Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.model:gemini-2.5-flash}") String model) {
        this.model = model;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY not set — AiTipService disabled, rule engine will be used for all suggestions.");
            this.client = null;
            this.enabled = false;
        } else {
            HttpOptions httpOptions = HttpOptions.builder()
                    .timeout(180000) // one batched call doing more reasoning — give it more room than the old per-category 8s
                    .build();
            this.client = Client.builder().apiKey(apiKey).httpOptions(httpOptions).build();
            this.enabled = true;
        }
    }

    /**
     * @param candidates   top-3 categories with amount/unit and their rule-based
     *                     fallback already computed (so a failure has zero extra cost)
     * @param breakdown    full 6-category % breakdown, for context
     * @param totalEmission total kg CO2 across all categories this window
     * @return exactly candidates.size() details, same order as candidates,
     *         either all GEMINI-sourced or all RULE_BASED-sourced (never mixed —
     *         a partial/malformed batch response is treated as a full failure
     *         rather than risking nulls in some slots).
     */
    public List<AiRecommendationDetail> generateBatch(List<CategoryCandidate> candidates,
                                                      List<CategoryBreakdownDto> breakdown,
                                                      BigDecimal totalEmission) {
        List<AiRecommendationDetail> fallback = candidates.stream()
                .map(CategoryCandidate::fallback)
                .toList();

        if (!enabled || candidates.isEmpty()) {
            return fallback;
        }

        try {
            String prompt = buildBatchPrompt(candidates, breakdown, totalEmission);
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(buildResponseSchema())
                    .build();

            GenerateContentResponse response = client.models.generateContent(model, prompt, config);
            String text = response.text();

            if (text == null || text.isBlank()) {
                log.warn("Gemini batch call returned empty response — using rule-based fallback for all categories.");
                return fallback;
            }

            List<GeminiCategoryResult> parsed = jsonMapper.readValue(text,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, GeminiCategoryResult.class));

            Map<String, GeminiCategoryResult> byCategory = new LinkedHashMap<>();
            for (GeminiCategoryResult r : parsed) {
                byCategory.put(r.category, r);
            }

            List<AiRecommendationDetail> result = new ArrayList<>();
            for (CategoryCandidate candidate : candidates) {
                GeminiCategoryResult r = byCategory.get(candidate.category().name());
                if (r == null || r.title == null || r.action == null) {
                    // Missing or incomplete entry for a requested category — don't
                    // silently mix a null slot in with real ones, fall back entirely.
                    log.warn("Gemini batch response missing/incomplete data for {} — using rule-based fallback for all categories.",
                            candidate.category());
                    return fallback;
                }
                result.add(new AiRecommendationDetail(
                        r.title, r.why, r.action,
                        r.alternatives != null ? r.alternatives : List.of(),
                        r.impact, "AI"
                ));
            }
            return result;

        } catch (Exception e) {
            // Deliberately broad: network, auth, rate limit, timeout, SDK error,
            // JSON parse failure — all fall back to the pre-built rule-based list.
            log.warn("Gemini batch call failed — using rule-based fallback for all categories.", e);
            return fallback;
        }
    }

    /**
     * Batched Gemini call for Goal Insights. Same guarantee as generateBatch():
     * NEVER throws, ALWAYS returns exactly one GoalInsightDetail per candidate,
     * same order. Any failure (disabled, network, timeout, malformed/incomplete
     * JSON, goalId mismatch) falls back to the pre-built rule-based insights
     * unchanged, with source = "STANDARD".
     */
    public List<GoalInsightDetail> generateGoalInsights(List<GoalInsightCandidate> candidates) {
        List<GoalInsightDetail> fallback = candidates.stream()
                .map(GoalInsightCandidate::fallback)
                .toList();

        if (!enabled || candidates.isEmpty()) {
            return fallback;
        }

        try {
            String prompt = buildGoalInsightPrompt(candidates);
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(buildGoalInsightResponseSchema())
                    .build();

            GenerateContentResponse response = client.models.generateContent(model, prompt, config);
            String text = response.text();

            if (text == null || text.isBlank()) {
                log.warn("Gemini goal-insight batch call returned empty response — using rule-based fallback for all goals.");
                return fallback;
            }

            List<GeminiGoalResult> parsed = jsonMapper.readValue(text,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, GeminiGoalResult.class));

            Map<Long, GeminiGoalResult> byGoalId = new LinkedHashMap<>();
            for (GeminiGoalResult r : parsed) {
                byGoalId.put(r.goalId, r);
            }

            List<GoalInsightDetail> result = new ArrayList<>();
            for (GoalInsightCandidate candidate : candidates) {
                GeminiGoalResult r = byGoalId.get(candidate.goalId());
                if (r == null || r.message == null || r.action == null) {
                    log.warn("Gemini goal-insight batch response missing/incomplete data for goal {} — using rule-based fallback for all goals.",
                            candidate.goalId());
                    return fallback;
                }
                result.add(new GoalInsightDetail(candidate.status().name(), r.message, r.action, "AI"));
            }
            return result;

        } catch (Exception e) {
            log.warn("Gemini goal-insight batch call failed — using rule-based fallback for all goals.", e);
            return fallback;
        }
    }

    private Schema buildGoalInsightResponseSchema() {
        Schema itemSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "goalId", Schema.builder().type(Type.Known.INTEGER).build(),
                        "message", Schema.builder().type(Type.Known.STRING).build(),
                        "action", Schema.builder().type(Type.Known.STRING).build()
                ))
                .required(List.of("goalId", "message", "action"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(itemSchema)
                .build();
    }

    private String buildGoalInsightPrompt(List<GoalInsightCandidate> candidates) {
        StringBuilder goalsBlock = new StringBuilder();
        for (GoalInsightCandidate c : candidates) {
            goalsBlock.append("- goalId ").append(c.goalId())
                    .append(": \"").append(c.goalName()).append("\" (").append(c.goalType()).append(")")
                    .append(", status ").append(c.status().name())
                    .append(", currently ").append(c.currentDisplay())
                    .append(" used against a ").append(c.targetDisplay()).append(" limit")
                    .append(", ").append(c.daysRemaining()).append(" days remaining until ").append(c.endDateDisplay())
                    .append(". Existing rule-based insight (safety reference only): \"")
                    .append(c.fallback().message()).append("\"\n");
        }

        return """
            You are EcoBot, the personalized sustainability assistant inside
            EcoTrack, a carbon footprint tracking application. The user has one
            or more active or recently-ended reduction goals.

            Analyze the goals below and return a JSON array with exactly one
            object per goal listed, each "goalId" matching exactly.

            RULES:
            1. Use ONLY the information explicitly given below.
            2. Never invent user habits, causes for being behind/over, or data not given.
            3. Do not open with "Great job", "Well done", or similar stock praise.
            4. Do not claim a specific numeric CO2 reduction unless it was given to you.
            5. Do not mention being an AI, a model, a prompt, or a fallback system.
                // In buildGoalInsightPrompt(...), replace rule 7 and add a new rule 9:
                6. Write in ONE consistent EcoBot voice: concise, practical, encouraging,
                   grounded in the data given — not artificially varied between goals.
                7. "action" is the primary, most visible field — make it punchy, concrete,
                   and specific to this goal's situation. "message" is secondary supporting
                   context: 1-2 lean sentences of data/context only. Do NOT restate the
                   goal's status in "message" (a status badge is already shown separately)
                   and do NOT repeat the action's content in "message".
                8. Use the given rule-based insight only as a safety reference; improve on
                   it with clearer, warmer, more specific phrasing for this exact situation.
                9. IMPORTANT: lower usage is always better here (these are consumption
                       caps, not progress bars) — status AT_RISK or OVER_BUDGET means the
                       user has used MORE than expected, not less.

            GOALS:
            %s

            For each goal return:
            - goalId: the integer id, copied exactly
            - message: 2-3 sentence narrative on where the user stands and what it means
            - action: one concrete, single-sentence next step
            """.formatted(goalsBlock.toString());
    }

    /** Input bundle for one goal's batch insight analysis. */
    public record GoalInsightCandidate(Long goalId, String goalName, String goalType,
                                       GoalInsightRuleEngine.Status status,
                                       String currentDisplay, String targetDisplay,
                                       String endDateDisplay, long daysRemaining,
                                       GoalInsightDetail fallback) {}

    /** Shape Gemini is asked to return per goal — internal parse target only. */
    private static class GeminiGoalResult {
        public Long goalId;
        public String message;
        public String action;
    }

    private Schema buildResponseSchema() {
        Schema itemSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "category", Schema.builder().type(Type.Known.STRING).build(),
                        "title", Schema.builder().type(Type.Known.STRING).build(),
                        "why", Schema.builder().type(Type.Known.STRING).build(),
                        "action", Schema.builder().type(Type.Known.STRING).build(),
                        "alternatives", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                        "impact", Schema.builder().type(Type.Known.STRING).build()
                ))
                .required(List.of("category", "title", "why", "action", "alternatives", "impact"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(itemSchema)
                .build();
    }

    private String buildBatchPrompt(List<CategoryCandidate> candidates,
                                    List<CategoryBreakdownDto> breakdown,
                                    BigDecimal totalEmission) {
        String profileLines = breakdown.stream()
                .filter(b -> b.getPercentage().compareTo(BigDecimal.ZERO) > 0)
                .map(b -> "- %s: %s%%".formatted(b.getCategory(), b.getPercentage().toPlainString()))
                .collect(java.util.stream.Collectors.joining("\n"));

        StringBuilder categoriesBlock = new StringBuilder();
        for (CategoryCandidate c : candidates) {
            String unitPart = (c.unit() == null || c.unit().isBlank()) ? "" : " " + c.unit();
            categoriesBlock.append("- ").append(c.category().name())
                    .append(": logged amount ").append(c.amount().toPlainString()).append(unitPart)
                    .append(" this period. Existing rule-based suggestion (safety reference only): \"")
                    .append(c.ruleBasedText()).append("\"\n");
        }

        return """
                You are EcoBot, the personalized sustainability recommendation assistant
                inside EcoTrack, a carbon footprint tracking application.

                Analyze the user's ACTUAL carbon-emission data below and return a JSON array
                with exactly one object per category listed under "CATEGORIES TO ANALYZE",
                in any order, each with the "category" field matching one of those names exactly.

                RULES:
                1. Use ONLY the information explicitly given below.
                2. Never invent user habits, vehicles, lifestyle choices, or any data not given.
                3. Do not judge the amount as good/bad without a baseline — stay neutral about
                   the number itself, put encouragement on the suggested action instead.
                4. Do not claim a specific numeric CO2 reduction unless it was given to you.
                5. Do not mention being an AI, a model, a prompt, or a fallback system.
                6. Do not open with "Great job", "Well done", or similar stock praise.
                7. Write in ONE consistent EcoBot voice across all categories: concise,
                   practical, encouraging, grounded in the data — not artificially varied.
                8. Use the given rule-based suggestion only as a safety reference; improve on
                   it using the fuller context you have here (the whole profile, not just one number).

                USER'S FULL EMISSION PROFILE (total %s kg CO2 across all logged categories):
                %s

                CATEGORIES TO ANALYZE:
                %s

                For each category return:
                - title: short phrase naming the opportunity (e.g. "Fuel is your biggest opportunity")
                - why: 1-2 sentences on why this category matters, using the data given
                - action: one concrete, single-sentence action
                - alternatives: exactly 2 more concrete actions relevant to that category
                - impact: qualitative explanation of why reducing this could help the user's
                  overall footprint — no invented numbers
                """.formatted(totalEmission.toPlainString(), profileLines, categoriesBlock.toString());
    }

    /** Input bundle for one category's batch analysis. */
    public record CategoryCandidate(CarbonCategory category, BigDecimal amount, String unit,
                                    String ruleBasedText, AiRecommendationDetail fallback) {}

    /** Shape Gemini is asked to return per category — internal parse target only. */
    private static class GeminiCategoryResult {
        public String category;
        public String title;
        public String why;
        public String action;
        public List<String> alternatives;
        public String impact;
    }
}