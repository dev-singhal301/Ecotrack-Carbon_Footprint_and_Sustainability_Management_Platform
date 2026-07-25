package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.dto.CarbonEntryResponse;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.CarbonEntryNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidCategoryException;
import com.ecotrack.ecotrack_backend.repository.UserRepository;
import com.ecotrack.ecotrack_backend.service.CarbonEntryService;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CarbonEntryController.class)
class CarbonEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private CarbonEntryService carbonEntryService;

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

        // Fakes a logged-in user with email "test@example.com"
        auth = new UsernamePasswordAuthenticationToken(
                "test@example.com",
                null,
                java.util.List.of() // empty authorities list — marks this token as authenticated
        );
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(testUser));
    }

    @Test
    void createEntry_validRequest_returns201() throws Exception {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("TRANSPORTATION");
        request.setAmount(new BigDecimal("10"));
        request.setUnit("km");
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        CarbonEntryResponse response = new CarbonEntryResponse(
                1L, "TRANSPORTATION", new BigDecimal("10"), "km",
                new BigDecimal("2.10"), LocalDate.of(2026, 7, 13));

        when(carbonEntryService.createEntry(any(CarbonEntryRequest.class), any(User.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/carbon-entries")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryId").value(1))
                .andExpect(jsonPath("$.category").value("TRANSPORTATION"));
    }

    @Test
    void createEntry_missingAmount_returns400() throws Exception {
        // amount is @NotNull @Positive — omit it to trigger validation failure
        String badJson = """
                {
                  "category": "TRANSPORTATION",
                  "unit": "km",
                  "activityDate": "2026-07-13"
                }
                """;

        mockMvc.perform(post("/api/carbon-entries")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createEntry_invalidCategory_returns400() throws Exception {
        CarbonEntryRequest request = new CarbonEntryRequest();
        request.setCategory("FLYING");
        request.setAmount(new BigDecimal("10"));
        request.setUnit("km");
        request.setActivityDate(LocalDate.of(2026, 7, 13));

        when(carbonEntryService.createEntry(any(CarbonEntryRequest.class), any(User.class)))
                .thenThrow(new InvalidCategoryException("Invalid category: FLYING"));

        mockMvc.perform(post("/api/carbon-entries")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid category: FLYING"));
    }

    @Test
    void getHistory_noFilters_returns200WithList() throws Exception {
        CarbonEntryResponse response = new CarbonEntryResponse(
                1L, "TRANSPORTATION", new BigDecimal("10"), "km",
                new BigDecimal("2.10"), LocalDate.of(2026, 7, 13));

        when(carbonEntryService.getHistory(eq(1L), isNull(), isNull(), isNull()))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/carbon-entries")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entryId").value(1))
                .andExpect(jsonPath("$[0].category").value("TRANSPORTATION"));
    }

    @Test
    void getHistory_withCategoryFilter_passesItThrough() throws Exception {
        when(carbonEntryService.getHistory(eq(1L), eq("WATER"), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/carbon-entries")
                        .param("category", "WATER")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getEntry_found_returns200() throws Exception {
        CarbonEntryResponse response = new CarbonEntryResponse(
                5L, "TRANSPORTATION", new BigDecimal("10"), "km",
                new BigDecimal("2.10"), LocalDate.of(2026, 7, 13));

        when(carbonEntryService.getEntryById(1L, 5L)).thenReturn(response);

        mockMvc.perform(get("/api/carbon-entries/5")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryId").value(5));
    }

    @Test
    void getEntry_notFound_returns404() throws Exception {
        when(carbonEntryService.getEntryById(1L, 999L))
                .thenThrow(new CarbonEntryNotFoundException("Carbon entry not found: 999"));

        mockMvc.perform(get("/api/carbon-entries/999")
                        .with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Carbon entry not found: 999"));
    }

    @Test
    void deleteEntry_found_returns204() throws Exception {
        mockMvc.perform(delete("/api/carbon-entries/5")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteEntry_notFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new CarbonEntryNotFoundException("Carbon entry not found: 999"))
                .when(carbonEntryService).deleteEntry(1L, 999L);

        mockMvc.perform(delete("/api/carbon-entries/999")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // Helper to inject a fake Authentication into MockMvc requests
    private static org.springframework.test.web.servlet.request.RequestPostProcessor authentication(Authentication auth) {
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}