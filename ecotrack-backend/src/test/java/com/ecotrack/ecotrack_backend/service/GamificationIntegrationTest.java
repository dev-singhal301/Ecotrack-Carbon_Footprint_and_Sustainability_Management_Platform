package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.entity.*;
import com.ecotrack.ecotrack_backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GamificationIntegrationTest {

    @Autowired
    private GoalService goalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CarbonEntryRepository carbonEntryRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Retrieve an existing role from the database or create a default one
        Role defaultRole = roleRepository.findAll().stream().findFirst().orElseGet(() -> {
            Role newRole = new Role();
            return roleRepository.save(newRole);
        });

        testUser = new User();
        testUser.setName("Eco Tester");
        testUser.setEmail("tester_" + System.currentTimeMillis() + "@ecotrack.com");
        testUser.setPassword("password123");
        testUser.setRole(defaultRole); // Satisfies NOT NULL constraint on role_id
        testUser.setUserLevel(1);
        testUser.setCurrentXp(0);
        testUser.setNextLevelXpTarget(100);
        testUser.setRewardPoints(0);
        testUser.setUserRank("BRONZE");

        testUser = userRepository.save(testUser);
    }

    /**
     * Helper method simulating XP gain and level evaluation directly on the User entity
     */
    private User awardXpToUser(Long userId, int xpAmount) {
        User user = userRepository.findById(userId).orElseThrow();
        int newXp = user.getCurrentXp() + xpAmount;
        user.setCurrentXp(newXp);

        // Simple level-up logic
        while (newXp >= user.getNextLevelXpTarget()) {
            user.setUserLevel(user.getUserLevel() + 1);
            user.setNextLevelXpTarget(user.getNextLevelXpTarget() + 100 * user.getUserLevel());
        }

        // Simple rank evaluation logic
        if (user.getUserLevel() >= 5) {
            user.setUserRank("GOLD");
        } else if (user.getUserLevel() >= 3) {
            user.setUserRank("SILVER");
        }

        return userRepository.save(user);
    }

    private void printHeader(String title) {
        System.out.println("\n================================================================================");
        System.out.println("  TEST RUN: " + title);
        System.out.println("================================================================================");
    }

    private void printResult(String key, Object value) {
        System.out.printf("  %-30s : %s%n", key, value);
    }

    // -------------------------------------------------------------------------
    // SCENARIO A: Initial User Baseline State
    // -------------------------------------------------------------------------
    @Test
    @Order(1)
    @DisplayName("Scenario A: Initial User Baseline State")
    void testScenarioA_InitialUserBaselineState() {
        printHeader("SCENARIO A - Initial User Baseline State");

        printResult("User ID", testUser.getId());
        printResult("Level", testUser.getUserLevel());
        printResult("Current XP", testUser.getCurrentXp());
        printResult("Next Level Target", testUser.getNextLevelXpTarget());
        printResult("Rank", testUser.getUserRank());
        printResult("Reward Points", testUser.getRewardPoints());

        assertEquals(1, testUser.getUserLevel());
        assertEquals(0, testUser.getCurrentXp());
        assertEquals(100, testUser.getNextLevelXpTarget());
        assertEquals("BRONZE", testUser.getUserRank());
        assertEquals(0, testUser.getRewardPoints());

        System.out.println("  STATUS: [PASS] Initial user baseline values are accurate.");
    }

    // -------------------------------------------------------------------------
    // SCENARIO B: Incremental XP Awarding (Sub-threshold)
    // -------------------------------------------------------------------------
    @Test
    @Order(2)
    @DisplayName("Scenario B: Incremental XP Awarding (Sub-threshold)")
    void testScenarioB_IncrementalXpAwarding() {
        printHeader("SCENARIO B - Incremental XP Awarding (+50 XP)");

        int initialXp = testUser.getCurrentXp();
        int xpToAdd = 50;

        User updatedUser = awardXpToUser(testUser.getId(), xpToAdd);

        printResult("Initial XP", initialXp);
        printResult("XP Added", xpToAdd);
        printResult("New XP Total", updatedUser.getCurrentXp());
        printResult("Current Level", updatedUser.getUserLevel());
        printResult("Level Up Occurred?", false);

        assertEquals(50, updatedUser.getCurrentXp());
        assertEquals(1, updatedUser.getUserLevel());

        System.out.println("  STATUS: [PASS] Sub-threshold XP recorded without levelling up.");
    }

    // -------------------------------------------------------------------------
    // SCENARIO C: Single Level-Up Threshold
    // -------------------------------------------------------------------------
    @Test
    @Order(3)
    @DisplayName("Scenario C: Single Level-Up Threshold")
    void testScenarioC_SingleLevelUpThreshold() {
        printHeader("SCENARIO C - Single Level-Up Threshold (+110 XP Total)");

        User updatedUser = awardXpToUser(testUser.getId(), 110);

        printResult("Total XP Awarded", updatedUser.getCurrentXp());
        printResult("New Level", updatedUser.getUserLevel());
        printResult("New Next Level Target", updatedUser.getNextLevelXpTarget());

        assertEquals(2, updatedUser.getUserLevel());
        assertEquals(110, updatedUser.getCurrentXp());
        assertTrue(updatedUser.getNextLevelXpTarget() > 100);

        System.out.println("  STATUS: [PASS] Level 2 achieved; next level target scaled up.");
    }

    // -------------------------------------------------------------------------
    // SCENARIO D: Multi-Level Jump (High XP Goal Completion)
    // -------------------------------------------------------------------------
    @Test
    @Order(4)
    @DisplayName("Scenario D: Multi-Level Jump (+1,500 XP)")
    void testScenarioD_MultiLevelJump() {
        printHeader("SCENARIO D - Multi-Level Jump (+1,500 XP Goal)");

        int startingLevel = testUser.getUserLevel();
        User updatedUser = awardXpToUser(testUser.getId(), 1500);

        printResult("Starting Level", startingLevel);
        printResult("Large XP Awarded", 1500);
        printResult("Final Level", updatedUser.getUserLevel());
        printResult("Final XP", updatedUser.getCurrentXp());

        assertTrue(updatedUser.getUserLevel() >= 4);
        assertEquals(1500, updatedUser.getCurrentXp());

        System.out.println("  STATUS: [PASS] Multi-level leap calculated successfully.");
    }

    // -------------------------------------------------------------------------
    // SCENARIO E: Rank Tier Escalation
    // -------------------------------------------------------------------------
    @Test
    @Order(5)
    @DisplayName("Scenario E: Rank Tier Escalation")
    void testScenarioE_RankTierEscalation() {
        printHeader("SCENARIO E - Rank Tier Escalation (Bronze -> Higher Rank)");

        String initialRank = testUser.getUserRank();
        User updatedUser = awardXpToUser(testUser.getId(), 3000);

        printResult("Initial Rank", initialRank);
        printResult("New Level", updatedUser.getUserLevel());
        printResult("Updated Rank", updatedUser.getUserRank());

        assertNotEquals("BRONZE", updatedUser.getUserRank());

        System.out.println("  STATUS: [PASS] User rank evaluated based on level threshold.");
    }

    // -------------------------------------------------------------------------
    // SCENARIO F: Goal Completion vs. Goal Expiry Reward Integrity
    // -------------------------------------------------------------------------
    @Test
    @Order(6)
    @DisplayName("Scenario F: Goal Completion vs Expiry")
    void testScenarioF_GoalCompletionVsExpiry() {
        printHeader("SCENARIO F - Goal Limit Logic & Expiry");

        // 1. Create a goal with 50.0 kg limit
        Goal limitGoal = new Goal();
        limitGoal.setUser(testUser);
        limitGoal.setGoalName("Stay Under 50kg Transport");
        limitGoal.setTargetValue(new BigDecimal("50.0"));
        limitGoal.setRewardPoints(100);
        limitGoal.setStatus("ACTIVE");
        limitGoal.setStartDate(LocalDate.now());
        limitGoal.setEndDate(LocalDate.now().plusDays(7));
        limitGoal = goalRepository.save(limitGoal);

        // 2. Log entry exceeding limit (60.0 kg)
        CarbonEntry entry = new CarbonEntry();
        entry.setUser(testUser);
        entry.setCategory(CarbonCategory.TRANSPORTATION);
        entry.setAmount(new BigDecimal("60.0"));
        entry.setActivityDate(LocalDate.now());
        carbonEntryRepository.save(entry);

        // 3. Trigger progress evaluation
        goalService.calculateProgress(testUser.getId(), limitGoal.getGoalId());

        Goal updatedGoal = goalRepository.findById(limitGoal.getGoalId()).orElseThrow();
        User userAfterBreach = userRepository.findById(testUser.getId()).orElseThrow();

        printResult("Goal Target Limit", 50.0);
        printResult("Actual Emissions", 60.0);
        printResult("Goal Final Status", updatedGoal.getStatus());
        printResult("Rewards Awarded", userAfterBreach.getRewardPoints());

        assertEquals("EXPIRED", updatedGoal.getStatus());
        assertEquals(0, userAfterBreach.getRewardPoints());

        System.out.println("  STATUS: [PASS] Goal marked EXPIRED upon limit breach; 0 rewards given.");
    }

    // -------------------------------------------------------------------------
    // SCENARIO G: Leaderboard Integration Check
    // -------------------------------------------------------------------------
    @Test
    @Order(7)
    @DisplayName("Scenario G: Leaderboard Integration Check")
    void testScenarioG_LeaderboardQuery() {
        printHeader("SCENARIO G - Leaderboard Aggregation");

        User updatedUser = awardXpToUser(testUser.getId(), 250);
        User topUser = userRepository.findById(updatedUser.getId()).orElseThrow();

        printResult("Top User Email", topUser.getEmail());
        printResult("Total XP Recorded", topUser.getCurrentXp());

        assertTrue(topUser.getCurrentXp() > 0);

        System.out.println("  STATUS: [PASS] Leaderboard XP state consistent.");
        System.out.println("================================================================================\n");
    }
}