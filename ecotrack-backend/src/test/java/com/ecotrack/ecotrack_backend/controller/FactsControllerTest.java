package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.FactResponse;
import com.ecotrack.ecotrack_backend.service.FactsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FactsController.class)
class FactsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FactsService factsService;

    // Not used directly by FactsController (no user lookup, per Decision H),
    // but required so JwtAuthFilter's constructor can be satisfied inside
    // this trimmed @WebMvcTest context — mirrors RecommendationControllerTest.
    @MockitoBean
    private com.ecotrack.ecotrack_backend.security.JwtUtil jwtUtil;

    @MockitoBean
    private com.ecotrack.ecotrack_backend.security.CustomUserDetailsService customUserDetailsService;

    private static final Authentication AUTH = new UsernamePasswordAuthenticationToken(
            "test@example.com", null, java.util.List.of()
    );

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated() {
        return SecurityMockMvcRequestPostProcessors.authentication(AUTH);
    }

    @Test
    void getRandomFact_returns200WithFactPayload() throws Exception {
        when(factsService.getRandomFact())
                .thenReturn(new FactResponse("A single mature tree can absorb up to 22 kg of CO2 per year."));

        mockMvc.perform(get("/api/facts/random").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fact").value("A single mature tree can absorb up to 22 kg of CO2 per year."));
    }

    @Test
    void getRandomFact_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/facts/random"))
                .andExpect(status().is4xxClientError());
    }
}