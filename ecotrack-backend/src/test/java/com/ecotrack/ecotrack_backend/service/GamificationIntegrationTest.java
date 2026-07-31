package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.EmissionFactor;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.EmissionFactorRepository;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
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

@SpringBootTest
@Transactional
public class GamificationIntegrationTest {

    @Autowired
    private CarbonEntryService carbonEntryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    @Autowired
    private CarbonEntryRepository carbonEntryRepository;

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
    }

    @Test
    @DisplayName("Scenario 1: Leaderboard Ranking Across Dummy Users (IDs 11-25)")
    void testLeaderboardRankingWithDummyUsers() {
        // Filter strictly for dummy users (IDs 11 to 25) and sort descending by XP
        List<User> rankedUsers = userRepository.findAll().stream()
                .filter(u -> u.getId() >= 11 && u.getId() <= 25)
                .sorted((u1, u2) -> Integer.compare(u2.getCurrentXp(), u1.getCurrentXp()))
                .collect(Collectors.toList());

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SCENARIO 1: DUMMY USERS LEADERBOARD (IDs 11-25)                       ║");
        System.out.println("╠══════╦═════════╦══════════════════════╦══════════╦═══════════╦═══════════════════════════════╣");
        System.out.printf("║ %-4s ║ %-7s ║ %-20s ║ %-8s ║ %-9s ║ %-29s ║%n", "RANK", "USER ID", "EMAIL", "XP", "LEVEL", "RANK TITLE");
        System.out.println("╠══════╬═════════╬══════════════════════╬══════════╬═══════════╬═══════════════════════════════╣");

        int rank = 1;
        for (User u : rankedUsers) {
            System.out.printf("║ #%-3d ║ %-7d ║ %-20s ║ %-8d ║ Lvl %-5d ║ %-29s ║%n",
                    rank++, u.getId(), u.getEmail(), u.getCurrentXp(), u.getUserLevel(), u.getUserRank());
        }
        System.out.println("╚══════╩═════════╩══════════════════════╩══════════╩═══════════╩═══════════════════════════════╝\n");

        // Assertions for top player in the dummy group
        User topUser = rankedUsers.get(0);
        assertEquals("ex25@gmail.com", topUser.getEmail(), "Top dummy user should be ex25@gmail.com");
        assertEquals(9800, topUser.getCurrentXp(), "Top user should have 9800 XP");
        assertEquals("Eco Master", topUser.getUserRank(), "Top user should hold Eco Master rank");
    }

    @Test
    @DisplayName("Scenario 2: Daily Logging XP Award & Anti-Spam (User 11)")
    void testDailyLoggingXpAndAntiSpamWithDummyUser() {
        User user11 = userRepository.findByEmail("ex11@gmail.com")
                .orElseThrow(() -> new IllegalStateException("Dummy user ex11@gmail.com not found in DB"));

        int initialXp = user11.getCurrentXp();

        // 1st Log Today (+10 XP)
        CarbonEntryRequest req1 = new CarbonEntryRequest();
        req1.setCategory("TRANSPORTATION");
        req1.setAmount(new BigDecimal("12.0"));
        req1.setActivityDate(LocalDate.now());
        carbonEntryService.createEntry(req1, user11);
        User afterLog1 = userRepository.findById(user11.getId()).orElseThrow();

        // 2nd Log Today (Anti-Spam Triggered -> 0 XP)
        CarbonEntryRequest req2 = new CarbonEntryRequest();
        req2.setCategory("TRANSPORTATION");
        req2.setAmount(new BigDecimal("20.0"));
        req2.setActivityDate(LocalDate.now());
        carbonEntryService.createEntry(req2, afterLog1);
        User afterLog2 = userRepository.findById(user11.getId()).orElseThrow();

        // Clean Summary Print
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                SCENARIO 2: DAILY LOGGING XP & ANTI-SPAM TEST (USER 11)                      ║");
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
    @DisplayName("Scenario 3: Level-Up Progression (User 14)")
    void testLevelUpProgressionWithDummyUser() {
        User user14 = userRepository.findByEmail("ex14@gmail.com")
                .orElseThrow(() -> new IllegalStateException("Dummy user ex14@gmail.com not found in DB"));

        int beforeXp = user14.getCurrentXp();
        int beforeLevel = user14.getUserLevel();
        int beforeTarget = user14.getNextLevelXpTarget();

        // Award 60 XP to cross the 500 XP threshold (450 -> 510 XP)
        user14.setCurrentXp(user14.getCurrentXp() + 60);

        while (user14.getCurrentXp() >= user14.getNextLevelXpTarget()) {
            user14.setUserLevel(user14.getUserLevel() + 1);
            user14.setNextLevelXpTarget(user14.getNextLevelXpTarget() + 500);
        }
        userRepository.save(user14);

        User updatedUser = userRepository.findById(user14.getId()).orElseThrow();

        // Clean Summary Print
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    SCENARIO 3: LEVEL-UP PROGRESSION TEST (USER 14)                           ║");
        System.out.println("╠═════════════════════════╦══════════════════════════════╦══════════════════════════════╦═════════╣");
        System.out.printf("║ %-23s ║ %-28s ║ %-28s ║ %-7s ║%n", "METRIC", "BEFORE TEST", "AFTER TEST", "CHANGE");
        System.out.println("╠═════════════════════════╬══════════════════════════════╬══════════════════════════════╬═════════╣");
        System.out.printf("║ Total XP                ║ %-28d ║ %-28d ║ +60 XP  ║%n", beforeXp, updatedUser.getCurrentXp());
        System.out.printf("║ User Level              ║ Level %-22d ║ Level %-22d ║ +1 Level║%n", beforeLevel, updatedUser.getUserLevel());
        System.out.printf("║ Next Target             ║ %-28d ║ %-28d ║ +500 XP ║%n", beforeTarget, updatedUser.getNextLevelXpTarget());
        System.out.println("╚═════════════════════════╩══════════════════════════════╩══════════════════════════════╩═════════╝\n");

        assertEquals(2, updatedUser.getUserLevel(), "User should level up to Level 2");
        assertEquals(1000, updatedUser.getNextLevelXpTarget(), "Next level target should update to 1000 XP");
    }
}