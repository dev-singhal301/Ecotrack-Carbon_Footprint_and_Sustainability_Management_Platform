package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.AiRecommendationDetail;
import com.ecotrack.ecotrack_backend.dto.CategoryBreakdownDto;
import com.ecotrack.ecotrack_backend.dto.RecommendationResponse;
import com.ecotrack.ecotrack_backend.dto.TopRecommendationDto;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.RecommendationService;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private com.ecotrack.ecotrack_backend.security.JwtUtil jwtUtil;

    @MockitoBean
    private com.ecotrack.ecotrack_backend.security.CustomUserDetailsService customUserDetailsService;

    private User testUser;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        auth = new UsernamePasswordAuthenticationToken(
                "test@example.com",
                null,
                java.util.List.of()
        );
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
    }

    // --- GET /api/recommendations ---

    @Test
    void getRecommendations_authenticated_returns200WithShape() throws Exception {
        RecommendationResponse response = new RecommendationResponse(
                List.of(new TopRecommendationDto("FUEL", BigDecimal.valueOf(38.0), BigDecimal.valueOf(6.2),
                        new AiRecommendationDetail("title", "why", "action", List.of("alt1", "alt2"), "impact", "RULE_BASED"))),
                List.of(new CategoryBreakdownDto("ELECTRICITY", new BigDecimal("100.0"))),
                null
        );

        when(recommendationService.getRecommendations(1L)).thenReturn(response);

        mockMvc.perform(get("/api/recommendations")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topRecommendations[0].category").value("FUEL"))
                .andExpect(jsonPath("$.topRecommendations[0].impactEstimate").value(6.2))
                .andExpect(jsonPath("$.allCategoriesSorted[0].category").value("ELECTRICITY"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void getRecommendations_zeroEmissions_returnsFallbackMessage() throws Exception {
        RecommendationResponse response = new RecommendationResponse(
                List.of(),
                List.of(new CategoryBreakdownDto("TRANSPORTATION", BigDecimal.ZERO)),
                "No emissions logged yet. Start tracking to get personalized tips!"
        );

        when(recommendationService.getRecommendations(1L)).thenReturn(response);

        mockMvc.perform(get("/api/recommendations")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topRecommendations").isEmpty())
                .andExpect(jsonPath("$.message").value("No emissions logged yet. Start tracking to get personalized tips!"));
    }

    @Test
    void getRecommendations_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().is4xxClientError());
    }

    // --- POST /api/recommendations/refresh ---

    @Test
    void refreshRecommendations_authenticated_returns200AndCallsService() throws Exception {
        RecommendationResponse response = new RecommendationResponse(
                List.of(new TopRecommendationDto("FUEL", BigDecimal.valueOf(38.0), BigDecimal.valueOf(6.2),
                        new AiRecommendationDetail("title", "why", "action", List.of("alt1", "alt2"), "impact", "RULE_BASED"))),
                List.of(new CategoryBreakdownDto("FUEL", new BigDecimal("100.0"))),
                null
        );

        when(recommendationService.refreshRecommendations(1L)).thenReturn(response);

        mockMvc.perform(post("/api/recommendations/refresh")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topRecommendations[0].category").value("FUEL"));
    }

    @Test
    void refreshRecommendations_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/recommendations/refresh")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    // Helper to inject a fake Authentication into MockMvc requests
    private static org.springframework.test.web.servlet.request.RequestPostProcessor authentication(Authentication auth) {
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}