package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.EmissionFactor;
import com.ecotrack.ecotrack_backend.entity.Role;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.EmissionFactorRepository;
import com.ecotrack.ecotrack_backend.repository.RoleRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fully self-contained: creates its own dummy users per test rather than
 * assuming specific seed rows (ex11@gmail.com etc.) already exist in the
 * DB. @Transactional rolls back everything created here after each test,
 * so nothing persists or collides with real dev data.
 */
@SpringBootTest
@Transactional
public class GamificationIntegrationTest {

    @Autowired
    private CarbonEntryService carbonEntryService;

    @Autowired
    private UserXpService userXpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    @Autowired
    private CarbonEntryRepository carbonEntryRepository;

    private Role dummyRole;

    @BeforeEach
    void setUp() {
        // Clear activity entries to prevent anti-spam interference across test runs
        carbonEntryRepository.deleteAll();

        // Ensure emission factor exists for transportation testing
        if (emissionFactorRepository.findByCategory(CarbonCategory.TRANSPORTATION).isEmpty()) {
            EmissionFactor factor = new EmissionFactor();
            factor.setCategory(CarbonCategory.TRANSPORTATION);
            factor.setFactorValue(new BigDecimal("0.21"));
            factor.setUnit("km");
            emissionFactorRepository.save(factor);
        }

        // Reuse the seeded ROLE_USER role if it exists; create it if this
        // environment has never seeded roles (keeps the test independent
        // of any particular DB's seed state).
        dummyRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
    }

    /**
     * Builds and persists a fully valid dummy user at a given starting XP,
     * with level/rank/target derived the same way the real app does.
     */
    private User createDummyUser(String email, int xp) {
        User user = new User();
        user.setName("Dummy " + email);
        user.setEmail(email);
        user.setPassword("test-password-not-used");
        user.setRole(dummyRole);
        user.setEnabled(true);
        user.setCurrentXp(xp);
        user.setUserLevel(GamificationUtils.getLevel(user));
        user.setUserRank(GamificationUtils.getRankTitle(user));
        user.setNextLevelXpTarget(GamificationUtils.getNextLevelXpTarget(user));
        return userRepository.save(user);
    }

    @Test
    @DisplayName("Scenario 1: Leaderboard Ranking Across Dummy Users")
    void testLeaderboardRankingWithDummyUsers() {
        User low = createDummyUser("dummy-low@ecotrack-test.com", 100);
        User mid = createDummyUser("dummy-mid@ecotrack-test.com", 2000);
        User top = createDummyUser("dummy-top@ecotrack-test.com", 9800);

        List<Long> createdIds = List.of(low.getId(), mid.getId(), top.getId());

        List<User> rankedUsers = userRepository.findAll().stream()
                .filter(u -> createdIds.contains(u.getId()))
                .sorted((u1, u2) -> Integer.compare(u2.getCurrentXp(), u1.getCurrentXp()))
                .collect(Collectors.toList());

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SCENARIO 1: DUMMY USERS LEADERBOARD                                   ║");
        System.out.println("╠══════╦═════════╦══════════════════════╦══════════╦═══════════╦═══════════════════════════════╣");
        System.out.printf("║ %-4s ║ %-7s ║ %-20s ║ %-8s ║ %-9s ║ %-29s ║%n", "RANK", "USER ID", "EMAIL", "XP", "LEVEL", "RANK TITLE");
        System.out.println("╠══════╬═════════╬══════════════════════╬══════════╬═══════════╬═══════════════════════════════╣");

        int rank = 1;
        for (User u : rankedUsers) {
            System.out.printf("║ #%-3d ║ %-7d ║ %-20s ║ %-8d ║ Lvl %-5d ║ %-29s ║%n",
                    rank++, u.getId(), u.getEmail(), u.getCurrentXp(), u.getUserLevel(), u.getUserRank());
        }
        System.out.println("╚══════╩═════════╩══════════════════════╩══════════╩═══════════╩═══════════════════════════════╝\n");

        User topUser = rankedUsers.get(0);
        assertEquals(top.getEmail(), topUser.getEmail(), "Top dummy user should be the one with the most XP");
        assertEquals(9800, topUser.getCurrentXp());
        // Rank title is derived, not hardcoded, so this stays correct even
        // if GamificationUtils' curve or milestone list changes later.
        assertEquals(GamificationUtils.getRankTitle(top), topUser.getUserRank());
    }

    @Test
    @DisplayName("Scenario 2: Daily Logging XP Award & Anti-Spam")
    void testDailyLoggingXpAndAntiSpamWithDummyUser() {
        User user = createDummyUser("dummy-anti-spam@ecotrack-test.com", 0);
        int initialXp = user.getCurrentXp();

        // 1st Log Today (+10 XP)
        CarbonEntryRequest req1 = new CarbonEntryRequest();
        req1.setCategory("TRANSPORTATION");
        req1.setAmount(new BigDecimal("12.0"));
        req1.setActivityDate(LocalDate.now());
        carbonEntryService.createEntry(req1, user);
        User afterLog1 = userRepository.findById(user.getId()).orElseThrow();

        // 2nd Log Today (Anti-Spam Triggered -> 0 XP)
        CarbonEntryRequest req2 = new CarbonEntryRequest();
        req2.setCategory("TRANSPORTATION");
        req2.setAmount(new BigDecimal("20.0"));
        req2.setActivityDate(LocalDate.now());
        carbonEntryService.createEntry(req2, afterLog1);
        User afterLog2 = userRepository.findById(user.getId()).orElseThrow();

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                SCENARIO 2: DAILY LOGGING XP & ANTI-SPAM TEST                                ║");
        System.out.println("╠══════════════════════════════════╦══════════════════════╦══════════════════════╦═════════════════╣");
        System.out.printf("║ %-32s ║ %-20s ║ %-20s ║ %-15s ║%n", "STAGE", "TOTAL XP", "XP CHANGE", "STATUS");
        System.out.println("╠══════════════════════════════════╬══════════════════════╬══════════════════════╬═════════════════╣");
        System.out.printf("║ 1. Initial Baseline              ║ %-20d ║ %-20s ║ Baseline        ║%n", initialXp, "0 XP");
        System.out.printf("║ 2. First Entry (Today)           ║ %-20d ║ %-20s ║ +10 XP Awarded  ║%n", afterLog1.getCurrentXp(), "+10 XP");
        System.out.printf("║ 3. Second Entry (Anti-Spam)      ║ %-20d ║ %-20s ║ Blocked (0 XP)  ║%n", afterLog2.getCurrentXp(), "+0 XP");
        System.out.println("╚══════════════════════════════════╩══════════════════════╩══════════════════════╩═════════════════╝\n");

        assertEquals(initialXp + 10, afterLog1.getCurrentXp(), "First log today should add 10 XP");
        assertEquals(initialXp + 10, afterLog2.getCurrentXp(), "XP should remain capped for 2nd entry today");
    }

    @Test
    @DisplayName("Scenario 3: Level-Up Progression")
    void testLevelUpProgressionWithDummyUser() {
        // Start just below the Level 2 threshold (50 XP per GamificationUtils)
        // so a modest award crosses it.
        User user = createDummyUser("dummy-levelup@ecotrack-test.com", 40);

        int beforeXp = user.getCurrentXp();
        int beforeLevel = user.getUserLevel();
        int beforeTarget = user.getNextLevelXpTarget();

        // Award XP through the real XP engine — not manual math — so this
        // test stays correct even if the underlying curve changes.
        userXpService.awardXpAndPoints(user.getId(), 20, 0, "TEST_LEVEL_UP", null);

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        int expectedLevel = GamificationUtils.getLevel(updatedUser);
        int expectedTarget = GamificationUtils.getNextLevelXpTarget(updatedUser);

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SCENARIO 3: LEVEL-UP PROGRESSION TEST                                     ║");
        System.out.println("╠═════════════════════════╦══════════════════════════════╦══════════════════════════════╦═════════╣");
        System.out.printf("║ %-23s ║ %-28s ║ %-28s ║ %-7s ║%n", "METRIC", "BEFORE TEST", "AFTER TEST", "CHANGE");
        System.out.println("╠═════════════════════════╬══════════════════════════════╬══════════════════════════════╬═════════╣");
        System.out.printf("║ Total XP                ║ %-28d ║ %-28d ║ +20 XP  ║%n", beforeXp, updatedUser.getCurrentXp());
        System.out.printf("║ User Level              ║ Level %-22d ║ Level %-22d ║ +%-6d║%n", beforeLevel, updatedUser.getUserLevel(), updatedUser.getUserLevel() - beforeLevel);
        System.out.printf("║ Next Target             ║ %-28d ║ %-28d ║ %-7s ║%n", beforeTarget, updatedUser.getNextLevelXpTarget(), "—");
        System.out.println("╚═════════════════════════╩══════════════════════════════╩══════════════════════════════╩═════════╝\n");

        assertEquals(beforeXp + 20, updatedUser.getCurrentXp(), "XP should have increased by exactly 20");
        assertTrue(updatedUser.getUserLevel() > beforeLevel, "User should have leveled up crossing the 50 XP threshold");
        assertEquals(expectedLevel, updatedUser.getUserLevel());
        assertEquals(expectedTarget, updatedUser.getNextLevelXpTarget());
    }
}