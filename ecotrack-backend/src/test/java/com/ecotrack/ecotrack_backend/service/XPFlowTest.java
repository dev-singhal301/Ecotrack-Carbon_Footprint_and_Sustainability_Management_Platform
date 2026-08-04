package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.LeaderboardUserDto;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.entity.XpTransaction;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.repository.XpTransactionRepository;
import com.ecotrack.ecotrack_backend.util.GamificationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XPFlowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private XpTransactionRepository xpTransactionRepository;

    @InjectMocks
    private UserXpService userXpService;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<XpTransaction> transactionCaptor;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(100L);
        testUser.setName("Eco Warrior");
        testUser.setEmail("warrior@ecotrack.com");
        testUser.setCurrentXp(0);
        testUser.setRewardPoints(0);
        testUser.setUserLevel(1);
        testUser.setUserRank("Novice Eco");
        testUser.setEnabled(true);
    }

    // =========================================================================
    // 1. PURE MATHEMATICAL & THRESHOLD UNIT TESTS (GamificationUtils)
    // =========================================================================
    @Nested
    @DisplayName("GamificationUtils Mathematical Boundary Tests")
    class GamificationUtilsTests {

        @Test
        @DisplayName("Level 1 boundaries (0 - 499 XP)")
        void testLevelOneBoundaries() {
            testUser.setCurrentXp(0);
            assertEquals(1, GamificationUtils.getLevel(testUser));

            testUser.setCurrentXp(250);
            assertEquals(1, GamificationUtils.getLevel(testUser));

            testUser.setCurrentXp(499);
            assertEquals(1, GamificationUtils.getLevel(testUser));
        }

        @Test
        @DisplayName("Level progression boundaries (500+ XP per level)")
        void testLevelProgressionBoundaries() {
            testUser.setCurrentXp(500);
            assertEquals(2, GamificationUtils.getLevel(testUser));

            testUser.setCurrentXp(1000);
            assertEquals(3, GamificationUtils.getLevel(testUser));
        }

        @Test
        @DisplayName("Next Level XP Target calculation accuracy")
        void testNextLevelTarget() {
            testUser.setCurrentXp(250); // Level 1 -> Target 500
            assertEquals(500, GamificationUtils.getNextLevelXpTarget(testUser));

            testUser.setCurrentXp(500); // Level 2 -> Target 1000
            assertEquals(1000, GamificationUtils.getNextLevelXpTarget(testUser));
        }

        @Test
        @DisplayName("Rank title mapping consistency for user level")
        void testRankMapping() {
            testUser.setCurrentXp(0); // Level 1
            assertEquals("Novice Eco", GamificationUtils.getRankTitle(testUser));

            testUser.setCurrentXp(500); // Level 2
            assertEquals("Eco Scout", GamificationUtils.getRankTitle(testUser));

            testUser.setCurrentXp(2500); // Level 6 (>= 5 is Eco Pioneer)
            assertEquals("Eco Pioneer", GamificationUtils.getRankTitle(testUser));
        }
    }

    // =========================================================================
    // 2. USER XP & LEVEL-UP LOGIC TESTS (UserXpService)
    // =========================================================================
    @Nested
    @DisplayName("UserXpService Core Execution Tests")
    class UserXpServiceTests {

        @Test
        @DisplayName("Award basic XP without leveling up creates transaction and updates balances")
        void awardXp_NoLevelUp() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

            userXpService.awardXpAndPoints(100L, 50, 20, "CARBON_ENTRY", 1L);

            verify(userRepository).save(userCaptor.capture());
            verify(xpTransactionRepository).save(transactionCaptor.capture());

            User savedUser = userCaptor.getValue();
            XpTransaction savedTx = transactionCaptor.getValue();

            assertEquals(50, savedUser.getCurrentXp());
            assertEquals(20, savedUser.getRewardPoints());
            assertEquals(1, savedUser.getUserLevel()); // Still Level 1

            assertEquals(50, savedTx.getXpAmount());
            assertEquals("CARBON_ENTRY", savedTx.getSourceType());
            assertEquals(1L, savedTx.getSourceId());
        }

        @Test
        @DisplayName("Awarding enough XP triggers Level Up and Rank promotion")
        void awardXp_TriggersLevelUpAndRankPromotion() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

            // Award 550 XP to cross Level 1 (500 threshold)
            userXpService.awardXpAndPoints(100L, 550, 100, "CHALLENGE_COMPLETION", 5L);

            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertEquals(550, savedUser.getCurrentXp());
            assertEquals(100, savedUser.getRewardPoints());
            assertTrue(savedUser.getUserLevel() > 1, "User level should have increased");
        }

        @Test
        @DisplayName("Multi-level jump from large single XP award")
        void awardXp_MultiLevelJump() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

            // Award large XP dump (5000 XP -> Level 11)
            userXpService.awardXpAndPoints(100L, 5000, 1000, "GOAL_COMPLETION", 2L);

            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertEquals(5000, savedUser.getCurrentXp());
            assertTrue(savedUser.getUserLevel() >= 5, "User level should jump significantly");
        }

        @Test
        @DisplayName("Zero or negative XP award handles safely without crashing")
        void awardXp_ZeroOrNegativeXp() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));

            userXpService.awardXpAndPoints(100L, 0, 0, "TEST_ZERO", null);

            verify(userRepository).save(userCaptor.capture());
            assertEquals(0, userCaptor.getValue().getCurrentXp());
        }

        @Test
        @DisplayName("Throws exception if user does not exist")
        void awardXp_UserNotFound_ThrowsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> 
                userXpService.awardXpAndPoints(999L, 100, 50, "CARBON_ENTRY", 1L)
            );
        }
    }

    // =========================================================================
    // 3. LEADERBOARD SERVICE PROJECTION & STANDING TESTS
    // =========================================================================
    @Nested
    @DisplayName("LeaderboardService Projection & Aggregation Tests")
    class LeaderboardServiceTests {

        @Test
        @DisplayName("All-time leaderboard maps user list correctly to DTOs")
        void getAllTimeLeaderboard_Success() {
            when(userRepository.findAllByOrderByCurrentXpDesc(any(Pageable.class)))
                    .thenReturn(List.of(testUser));

            List<LeaderboardUserDto> leaderboard = leaderboardService.getAllTimeLeaderboard(10);

            assertNotNull(leaderboard);
            assertEquals(1, leaderboard.size());
            assertEquals("Eco Warrior", leaderboard.get(0).getName());
            assertEquals(1L, leaderboard.get(0).getRank());
        }

        @Test
        @DisplayName("Monthly leaderboard maps raw object array native projections cleanly")
        void getMonthlyLeaderboard_MapsRawNativeQuery() {
            // Simulated Object[] row returned by native SQL query:
            // [userId, name, userRank, userLevel, monthlyXp, denseRank]
            Object[] rawRow = new Object[]{100L, "Eco Warrior", "Novice Eco", 1, 250L, 1L};
            
            when(xpTransactionRepository.findMonthlyLeaderboardRaw(10))
                    .thenReturn(java.util.Collections.singletonList(rawRow));
        
            List<LeaderboardUserDto> leaderboard = leaderboardService.getMonthlyLeaderboard(10);
        
            assertNotNull(leaderboard);
            assertEquals(1, leaderboard.size());
        
            LeaderboardUserDto dto = leaderboard.get(0);
            assertEquals(100L, dto.getUserId());
            assertEquals("Eco Warrior", dto.getName());
            assertEquals("Novice Eco", dto.getUserRank());
            assertEquals(1, dto.getUserLevel());
            assertEquals(250L, dto.getMonthlyXp());
            assertEquals(1L, dto.getRank());
        }

        @Test
        @DisplayName("Calculates exact overall standing position based on XP threshold")
        void getUserRankPosition_Success() {
            testUser.setCurrentXp(450);
            when(userRepository.findById(100L)).thenReturn(Optional.of(testUser));
            when(userRepository.countByCurrentXpGreaterThan(450)).thenReturn(3L);

            LeaderboardUserDto standing = leaderboardService.getUserRankPosition(100L);

            assertNotNull(standing);
            assertEquals(4L, standing.getRank(), "Rank should be (count higher XP + 1) = 4");
            assertEquals(450L, standing.getMonthlyXp());
        }
    }
}