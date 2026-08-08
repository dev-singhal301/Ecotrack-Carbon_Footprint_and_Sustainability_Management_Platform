package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.ActivityLogRequest;
import com.ecotrack.ecotrack_backend.dto.ChallengeResponse;
import com.ecotrack.ecotrack_backend.dto.UserChallengeResponse;
import com.ecotrack.ecotrack_backend.entity.*;
import com.ecotrack.ecotrack_backend.exception.ChallengeNotFoundException;
import com.ecotrack.ecotrack_backend.exception.DuplicateChallengeParticipationException;
import com.ecotrack.ecotrack_backend.exception.JoinWindowClosedException;
import com.ecotrack.ecotrack_backend.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User-facing participation lifecycle for challenges: join, activity
 * logging, carbon-progress tracking, completion, and expiration.
 *
 * Split of responsibility from ChallengeService (admin create/edit/cancel):
 * this class owns everything from Challenge Module Design doc §5 onward —
 * join rules, §6 carbon-reduction baseline math, §7 activity logging, and
 * the completion/expiration state machine in §11-12.
 */
@Service
public class UserChallengeService {

    // Historical window used to compute a carbon-reduction challenge's baseline.
    private static final int BASELINE_WINDOW_DAYS = 30;

    private final ChallengeRepository challengeRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;
    private final CarbonEntryRepository carbonEntryRepository;
    private final ChallengeActivityLogRepository activityLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserChallengeService(
            ChallengeRepository challengeRepository,
            UserChallengeRepository userChallengeRepository,
            UserRepository userRepository,
            LeaderboardService leaderboardService,
            CarbonEntryRepository carbonEntryRepository,
            ChallengeActivityLogRepository activityLogRepository,
            ApplicationEventPublisher eventPublisher) {
        this.challengeRepository = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
        this.carbonEntryRepository = carbonEntryRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── Read ──

    @Transactional
    public List<UserChallengeResponse> getUserChallenges(String userEmail) {
        User user = findUserByEmailOrThrow(userEmail);

        List<Challenge> challenges = challengeRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> !c.isCancelled())
                .toList();

        Map<Long, UserChallenge> userChallengeMap = userChallengeRepository.findByUserId(user.getId())
                .stream()
                .collect(Collectors.toMap(uc -> uc.getChallenge().getChallengeId(), Function.identity()));

        // Lazily bring every in-progress participation up to date — mirrors
        // GoalService's checkAndHandleExpiration pattern. Carbon challenges
        // get their currentValue refreshed from carbon_entries every time;
        // both types get finalized (COMPLETED/EXPIRED) if their period has
        // ended since we last looked.
        userChallengeMap.values().forEach(this::refreshAndFinalize);

        return challenges.stream()
                .map(challenge -> {
                    ChallengeResponse challengeDto = ChallengeResponse.fromEntity(challenge);
                    UserChallenge userChallenge = userChallengeMap.get(challenge.getChallengeId());
                    UserChallengeResponse response = UserChallengeResponse.fromEntity(challengeDto, userChallenge);
                    response.setLoggedToday(computeLoggedToday(userChallenge));
                    return response;
                })
                .toList();
    }

    // ── Scheduled Maintenance: force-settle every in-progress participation
    // whose window has closed, even if the user hasn't opened the app since.
    // Reuses refreshAndFinalize, the exact same logic getUserChallenges()
    // relies on for lazy, per-user finalization. ──
    @Transactional
    public int finalizeAllInProgressChallenges() {
        List<UserChallenge> inProgress = userChallengeRepository.findByStatus(UserChallengeStatus.IN_PROGRESS);
        inProgress.forEach(this::refreshAndFinalize);
        return inProgress.size();
    }

    // ── Join ──

    @Transactional
    public UserChallengeResponse joinChallenge(String userEmail, Long challengeId) {
        User user = findUserByEmailOrThrow(userEmail);
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Challenge not found: " + challengeId));

        if (challenge.isCancelled()) {
            throw new IllegalStateException("Cannot join a cancelled challenge");
        }

        LocalDate today = LocalDate.now();

        // Join window: start_date through start_date + JOIN_WINDOW_DAYS,
        // inclusive (Design doc §5 / Workflow doc §3, §16).
        if (today.isBefore(challenge.getStartDate())) {
            throw new JoinWindowClosedException("This challenge hasn't started yet");
        }
        LocalDate joinDeadline = challenge.getStartDate().plusDays(Challenge.JOIN_WINDOW_DAYS);
        if (today.isAfter(joinDeadline)) {
            throw new JoinWindowClosedException("The join window for this challenge has closed");
        }
        // A short challenge (duration < JOIN_WINDOW_DAYS) can have its
        // end_date fall before the start_date-relative join deadline above.
        // Without this check a user could join a challenge that's already
        // over, leaving zero days to actually track anything — which is
        // exactly what produced the "0 kg / 100% / already Completed" bug.
        if (today.isAfter(challenge.getEndDate())) {
            throw new JoinWindowClosedException("This challenge has already ended");
        }

        if (userChallengeRepository.existsByUserIdAndChallengeId(user.getId(), challengeId)) {
            throw new DuplicateChallengeParticipationException("User has already joined this challenge");
        }

        UserChallenge userChallenge = new UserChallenge();
        userChallenge.setUser(user);
        userChallenge.setChallenge(challenge);
        userChallenge.setStatus(UserChallengeStatus.IN_PROGRESS);
        userChallenge.setCurrentValue(BigDecimal.ZERO);
        userChallenge.setCompletionPercentage(BigDecimal.ZERO);
        userChallenge.setJoinedAt(LocalDateTime.now());

        if (challenge.getChallengeType() == ChallengeType.CARBON_REDUCTION) {
            applyCarbonBaseline(userChallenge, challenge, user.getId(), today);
        }

        UserChallenge saved = userChallengeRepository.save(userChallenge);
        return UserChallengeResponse.fromEntity(ChallengeResponse.fromEntity(challenge), saved);
    }

    // ── Activity logging (ACTIVITY_BASED challenges only) ──

    /**
     * Records one activity event and immediately re-derives progress from
     * the sum of all logs — never from a client-supplied total (Design doc
     * §7: "this naturally prevents crediting activity that happened before
     * the user joined" and prevents the "just type progress = 100%" abuse
     * case the design explicitly calls out for carbon challenges too).
     *
     * COUNT/BOOLEAN types are capped at one log per calendar day and always
     * log exactly 1, regardless of what the client sends.
     */
    @Transactional
    public UserChallengeResponse logActivity(String userEmail, Long challengeId, ActivityLogRequest request) {
        User user = findUserByEmailOrThrow(userEmail);
        UserChallenge userChallenge = userChallengeRepository.findByUserIdAndChallengeId(user.getId(), challengeId)
                .orElseThrow(() -> new IllegalArgumentException("User has not joined this challenge"));

        Challenge challenge = userChallenge.getChallenge();

        if (challenge.getChallengeType() != ChallengeType.ACTIVITY_BASED) {
            throw new IllegalStateException("Only activity-based challenges accept activity logs");
        }
        if (userChallenge.getStatus() != UserChallengeStatus.IN_PROGRESS) {
            throw new IllegalStateException("This challenge is no longer in progress");
        }

        LocalDate today = LocalDate.now();
        if (today.isAfter(challenge.getEndDate())) {
            finalizeActivityIfEnded(userChallenge);
            throw new IllegalStateException("This challenge has already ended");
        }

        TrackingType trackingType = challenge.getTrackingType();
        BigDecimal amount;

        if (trackingType == TrackingType.COUNT || trackingType == TrackingType.BOOLEAN) {
            // Anti-abuse: max one log per day for check-in-style tracking
            // types, and the amount is never client-supplied.
            if (activityLogRepository.existsByUserChallengeIdAndLogDate(userChallenge.getId(), today)) {
                throw new IllegalStateException("Only one log per day is allowed for this challenge");
            }
            amount = BigDecimal.ONE;
        } else {
            if (request == null || request.getAmount() == null) {
                throw new IllegalArgumentException("Amount is required for this challenge's tracking type");
            }
            amount = request.getAmount();
        }

        ChallengeActivityLog log = new ChallengeActivityLog();
        log.setUserChallenge(userChallenge);
        log.setLogDate(today);
        log.setAmount(amount);
        activityLogRepository.save(log);

        BigDecimal newTotal = activityLogRepository.findByUserChallengeId(userChallenge.getId()).stream()
                .map(ChallengeActivityLog::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal target = challenge.getTargetValue();
        // Cap what's stored/displayed at the target itself — a single log
        // (e.g. typing "100" against a 50-tree target) can jump straight
        // past it in one call, and showing "100 / 50 trees (100%)" reads as
        // broken even though it's technically accurate. The actual logged
        // amount stays in ChallengeActivityLog untouched either way; only
        // this rollup is clamped.
        BigDecimal displayValue = (target != null && newTotal.compareTo(target) > 0) ? target : newTotal;
        userChallenge.setCurrentValue(displayValue);
        userChallenge.setCompletionPercentage(activityPercentage(displayValue, target));
        userChallengeRepository.save(userChallenge);

        if (target != null && newTotal.compareTo(target) >= 0) {
            // Reached the fixed target — completes immediately, not deferred
            // to challenge end (Workflow doc §7: "5/5 reached -> COMPLETED").
            completeChallenge(userChallenge);
        }

        UserChallengeResponse response = UserChallengeResponse.fromEntity(ChallengeResponse.fromEntity(challenge), userChallenge);
        response.setLoggedToday(computeLoggedToday(userChallenge));
        return response;
    }

    // ── Manual claim (idempotent finalize-and-award, kept for the existing UI flow) ──

    /**
     * Finalizes and awards XP for a challenge the user has already reached
     * the target on. In normal operation this is redundant — logActivity
     * and the lazy carbon-period check in getUserChallenges both finalize
     * automatically — but it stays as an explicit, idempotent action the
     * frontend can call directly rather than relying solely on incidental
     * recalculation elsewhere.
     */
    @Transactional
    public UserChallengeResponse claimReward(String userEmail, Long challengeId) {
        User user = findUserByEmailOrThrow(userEmail);
        UserChallenge userChallenge = userChallengeRepository.findByUserIdAndChallengeId(user.getId(), challengeId)
                .orElseThrow(() -> new IllegalArgumentException("User challenge record not found"));

        if (userChallenge.getStatus() == UserChallengeStatus.COMPLETED) {
            throw new IllegalStateException("Reward already claimed for this challenge");
        }

        Challenge challenge = userChallenge.getChallenge();
        boolean eligible;

        if (challenge.getChallengeType() == ChallengeType.CARBON_REDUCTION) {
            // A carbon challenge can be judged early as a failure the
            // moment the budget is blown, but can only be judged a success
            // once its period has actually ended — see finalizeCarbonIfEnded.
            recalcCarbonCurrentValue(userChallenge);
            userChallengeRepository.save(userChallenge);

            if (isOverCarbonBudget(userChallenge)) {
                expireChallenge(userChallenge);
                throw new IllegalStateException("This challenge's budget was exceeded — it has expired");
            }

            LocalDate today = LocalDate.now();
            boolean periodEnded = !today.isBefore(challenge.getEndDate());
            eligible = periodEnded && userChallenge.getEffectiveTarget() != null;

            if (!periodEnded) {
                throw new IllegalStateException("This challenge hasn't ended yet — keep tracking within budget");
            }
        } else {
            eligible = userChallenge.getCompletionPercentage() != null
                    && userChallenge.getCompletionPercentage().compareTo(new BigDecimal("100")) >= 0;
        }

        if (!eligible) {
            throw new IllegalStateException("Challenge is not fully completed yet");
        }

        completeChallenge(userChallenge);
        return UserChallengeResponse.fromEntity(ChallengeResponse.fromEntity(challenge), userChallenge);
    }

    // ── Carbon-reduction progress refresh, triggered from CarbonEntryService ──

    /**
     * Called after a carbon entry is created or deleted so active
     * carbon-reduction challenges reflect the change without the user
     * having to reload the challenges page first. Purely a freshness
     * optimization — getUserChallenges() recomputes the same thing lazily
     * on every read regardless.
     */
    @Transactional
    public void recalculateCarbonChallengesForUser(Long userId) {
        userChallengeRepository.findByUserId(userId).stream()
                .filter(uc -> uc.getStatus() == UserChallengeStatus.IN_PROGRESS)
                .filter(uc -> uc.getChallenge().getChallengeType() == ChallengeType.CARBON_REDUCTION)
                .filter(uc -> !uc.getChallenge().isCancelled())
                .forEach(this::refreshAndFinalize);
    }

    // ── Internal: "logged today" flag for COUNT/BOOLEAN activity challenges ──

    /**
     * Null when not applicable — anything other than an IN_PROGRESS
     * ACTIVITY_BASED challenge with a once-per-day tracking type (COUNT /
     * BOOLEAN). For those, reflects whether today's single allowed log has
     * already been submitted, straight from the activity_logs table rather
     * than any in-memory/session state, so the frontend's button stays
     * disabled correctly across page reloads and navigation.
     */
    private Boolean computeLoggedToday(UserChallenge userChallenge) {
        if (userChallenge == null || userChallenge.getStatus() != UserChallengeStatus.IN_PROGRESS) {
            return null;
        }
        Challenge challenge = userChallenge.getChallenge();
        if (challenge.getChallengeType() != ChallengeType.ACTIVITY_BASED) {
            return null;
        }
        TrackingType trackingType = challenge.getTrackingType();
        if (trackingType != TrackingType.COUNT && trackingType != TrackingType.BOOLEAN) {
            return null;
        }
        return activityLogRepository.existsByUserChallengeIdAndLogDate(userChallenge.getId(), LocalDate.now());
    }

    // ── Internal: refresh + finalize ──

    private void refreshAndFinalize(UserChallenge userChallenge) {
        Challenge challenge = userChallenge.getChallenge();
        if (challenge.isCancelled()) {
            return; // handled by ChallengeService's cancellation cascade instead
        }

        // Carbon-reduction challenges that already finished successfully:
        // keep currentValue/completionPercentage live instead of freezing
        // them at whatever they happened to be on the exact call that
        // finalized the challenge. Safe on every read — periodEnd inside
        // recalcCarbonCurrentValue is always capped at the challenge's
        // end_date, so this settles on the same correct final numbers no
        // matter how many times, or how long after completion, it runs.
        // This also self-heals any already-COMPLETED rows left showing
        // stale join-time values (0 kg / 100% budget) from before
        // completeChallenge() correctly stopped forcing carbon-reduction
        // challenges to 100% on completion.
        if (challenge.getChallengeType() == ChallengeType.CARBON_REDUCTION
                && userChallenge.getStatus() == UserChallengeStatus.COMPLETED) {
            recalcCarbonCurrentValue(userChallenge);
            userChallengeRepository.save(userChallenge);
            return;
        }

        if (userChallenge.getStatus() != UserChallengeStatus.IN_PROGRESS) {
            return;
        }

        if (challenge.getChallengeType() == ChallengeType.CARBON_REDUCTION) {
            recalcCarbonCurrentValue(userChallenge);
            userChallengeRepository.save(userChallenge);

            // Budget blown -> expire right away, don't wait for end_date.
            // Only once they're still within budget do we fall back to
            // "has the period actually finished yet" to decide completion.
            if (isOverCarbonBudget(userChallenge)) {
                expireChallenge(userChallenge);
            } else {
                finalizeCarbonIfEnded(userChallenge);
            }
        } else {
            finalizeActivityIfEnded(userChallenge);
        }
    }

    /**
     * True once logged emissions have pushed past the allowed budget for
     * this challenge. Checked independently of the challenge's end date so
     * a user can be expired mid-challenge instead of only at period end.
     */
    private boolean isOverCarbonBudget(UserChallenge userChallenge) {
        BigDecimal target = userChallenge.getEffectiveTarget();
        BigDecimal current = userChallenge.getCurrentValue();
        return target != null && current != null && current.compareTo(target) > 0;
    }

    private void finalizeCarbonIfEnded(UserChallenge userChallenge) {
        Challenge challenge = userChallenge.getChallenge();
        if (!LocalDate.now().isAfter(challenge.getEndDate())) {
            return; // still running and still within budget — no verdict yet
        }
        // Reached only when the period is over AND isOverCarbonBudget just
        // returned false, i.e. a genuine success — unless we never had a
        // valid target to judge against, in which case there's nothing to
        // credit them for.
        if (userChallenge.getEffectiveTarget() == null) {
            expireChallenge(userChallenge);
            return;
        }
        completeChallenge(userChallenge);
    }

    private void finalizeActivityIfEnded(UserChallenge userChallenge) {
        Challenge challenge = userChallenge.getChallenge();
        if (userChallenge.getStatus() != UserChallengeStatus.IN_PROGRESS) {
            return;
        }
        if (!LocalDate.now().isAfter(challenge.getEndDate())) {
            return;
        }
        BigDecimal target = challenge.getTargetValue();
        boolean reached = target != null && userChallenge.getCurrentValue() != null
                && userChallenge.getCurrentValue().compareTo(target) >= 0;
        if (reached) {
            completeChallenge(userChallenge);
        } else {
            expireChallenge(userChallenge);
        }
    }

    private void completeChallenge(UserChallenge userChallenge) {
        userChallenge.setStatus(UserChallengeStatus.COMPLETED);
        userChallenge.setCompletedAt(LocalDateTime.now());
        if (userChallenge.getChallenge().getChallengeType() != ChallengeType.CARBON_REDUCTION) {
            // Activity (and legacy untyped) challenges: 100% means target
            // reached. Carbon challenges keep their budget-remaining
            // reading — see computeCarbonBudgetPercentage's javadoc.
            userChallenge.setCompletionPercentage(BigDecimal.valueOf(100));
        }
        UserChallenge saved = userChallengeRepository.save(userChallenge);

        int xpReward = saved.getChallenge().getRewardXp() != null ? saved.getChallenge().getRewardXp() : 100;
        leaderboardService.awardXp(saved.getUser().getId(), xpReward, "CHALLENGE", saved.getChallenge().getChallengeId());

        eventPublisher.publishEvent(new ChallengeCompletedEvent(
                this, saved.getUser().getId(), saved.getChallenge().getChallengeId(), saved.getId(), xpReward));
    }

    private void expireChallenge(UserChallenge userChallenge) {
        userChallenge.setStatus(UserChallengeStatus.EXPIRED);
        // Reuses the same completedAt column to mean "the moment this
        // participation was resolved" (completed OR expired), not literally
        // "completed" — avoids a schema change just to support sorting the
        // Completed/Expired tabs by recency. UserChallengeResponse still
        // exposes it as completedAt; the frontend treats it as a generic
        // resolvedAt when the outcome is EXPIRED.
        userChallenge.setCompletedAt(LocalDateTime.now());
        UserChallenge saved = userChallengeRepository.save(userChallenge);

        eventPublisher.publishEvent(new ChallengeExpiredEvent(
                this, saved.getUser().getId(), saved.getChallenge().getChallengeId(), saved.getId()));
    }

    // ── Carbon-reduction math (Design doc §6, §9) ──

    private void applyCarbonBaseline(UserChallenge userChallenge, Challenge challenge, Long userId, LocalDate today) {
        CarbonCategory category = mapToCarbonCategory(challenge.getCategory());

        LocalDate baselineStart = today.minusDays(BASELINE_WINDOW_DAYS);
        LocalDate baselineEnd = today.minusDays(1);
        BigDecimal historicalBaseline = sumEmissions(userId, category, baselineStart, baselineEnd);
        BigDecimal dailyRate = historicalBaseline.divide(BigDecimal.valueOf(BASELINE_WINDOW_DAYS), 10, RoundingMode.HALF_UP);

        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(today, challenge.getEndDate()));
        BigDecimal scaledBaseline = dailyRate.multiply(BigDecimal.valueOf(remainingDays)).setScale(4, RoundingMode.HALF_UP);

        BigDecimal reductionFraction = BigDecimal.ONE.subtract(
                challenge.getTargetValue().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        BigDecimal effectiveTarget = scaledBaseline.multiply(reductionFraction).setScale(4, RoundingMode.HALF_UP);
        if (effectiveTarget.compareTo(BigDecimal.ZERO) < 0) {
            effectiveTarget = BigDecimal.ZERO;
        }

        Long maxEntryId = carbonEntryRepository.findByUserId(userId).stream()
                .map(CarbonEntry::getEntryId)
                .max(Long::compare)
                .orElse(0L);

        userChallenge.setBaselineValue(scaledBaseline);
        userChallenge.setStartEntryId(maxEntryId);
        userChallenge.setEffectiveTarget(effectiveTarget);
        userChallenge.setCompletionPercentage(computeCarbonBudgetPercentage(BigDecimal.ZERO, effectiveTarget));
    }

    private void recalcCarbonCurrentValue(UserChallenge userChallenge) {
        Challenge challenge = userChallenge.getChallenge();
        LocalDate today = LocalDate.now();
        LocalDate periodEnd = today.isAfter(challenge.getEndDate()) ? challenge.getEndDate() : today;
        LocalDate periodStart = userChallenge.getJoinedAt().toLocalDate();
        if (periodStart.isAfter(periodEnd)) {
            periodStart = periodEnd;
        }

        CarbonCategory category = mapToCarbonCategory(challenge.getCategory());
        List<CarbonEntry> entries = (category != null)
                ? carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                        userChallenge.getUser().getId(), category, periodStart, periodEnd)
                : carbonEntryRepository.findByUserIdAndActivityDateBetween(
                        userChallenge.getUser().getId(), periodStart, periodEnd);

        long anchor = userChallenge.getStartEntryId() != null ? userChallenge.getStartEntryId() : 0L;
        BigDecimal total = entries.stream()
                .filter(e -> e.getEntryId() > anchor)
                .map(CarbonEntry::getCarbonEmission)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        userChallenge.setCurrentValue(total);
        userChallenge.setCompletionPercentage(computeCarbonBudgetPercentage(total, userChallenge.getEffectiveTarget()));
    }

    /**
     * Carbon-reduction challenges are a ceiling, not an accumulator — unlike
     * an activity challenge's "sum toward a target" (where 100% = done),
     * currentValue here is emissions consumed against an allowed budget, so
     * we report the inverse: how much budget is left. 100% = none of the
     * budget used yet; 0% = at or over the ceiling. This deliberately does
     * NOT reuse GoalService's raw current/target percentage, which would
     * read backwards here (100% would mean "just breached the target").
     * Whether the challenge is actually won or lost is decided separately,
     * only once the period ends — see finalizeCarbonIfEnded.
     */
    private BigDecimal computeCarbonBudgetPercentage(BigDecimal currentValue, BigDecimal effectiveTarget) {
        if (effectiveTarget == null || effectiveTarget.compareTo(BigDecimal.ZERO) <= 0) {
            boolean atZero = currentValue != null && currentValue.compareTo(BigDecimal.ZERO) <= 0;
            return atZero ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        BigDecimal used = currentValue.multiply(BigDecimal.valueOf(100))
                .divide(effectiveTarget, 2, RoundingMode.HALF_UP);
        BigDecimal remaining = BigDecimal.valueOf(100).subtract(used);
        if (remaining.compareTo(BigDecimal.valueOf(100)) > 0) remaining = BigDecimal.valueOf(100);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
        return remaining;
    }

    private BigDecimal activityPercentage(BigDecimal currentValue, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal percentage = currentValue.multiply(BigDecimal.valueOf(100))
                .divide(target, 2, RoundingMode.HALF_UP);
        return percentage.compareTo(BigDecimal.valueOf(100)) > 0 ? BigDecimal.valueOf(100) : percentage;
    }

    private BigDecimal sumEmissions(Long userId, CarbonCategory category, LocalDate from, LocalDate to) {
        List<CarbonEntry> entries = (category != null)
                ? carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(userId, category, from, to)
                : carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);

        return entries.stream()
                .map(CarbonEntry::getCarbonEmission)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Challenges use their own ChallengeCategory (OVERALL + the six shared
    // names); OVERALL means "sum across every category" -> null filter.
    private CarbonCategory mapToCarbonCategory(String challengeCategory) {
        if (challengeCategory == null || "OVERALL".equalsIgnoreCase(challengeCategory)) {
            return null;
        }
        try {
            return CarbonCategory.valueOf(challengeCategory.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }
}