package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.FactResponse;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FactsServiceTest {

    private final FactsService factsService = new FactsService();

    @Test
    void getRandomFact_returnsNonNullNonEmptyFact() {
        FactResponse response = factsService.getRandomFact();

        assertNotNull(response);
        assertNotNull(response.getFact());
        assertFalse(response.getFact().isBlank());
    }

    @Test
    void getRandomFact_returnsVariedFactsAcrossManyCalls() {
        // Not deterministic by nature, but with 30+ facts and 50 draws,
        // seeing more than one distinct fact confirms randomness is actually
        // wired up rather than always returning index 0.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(factsService.getRandomFact().getFact());
        }

        assertTrue(seen.size() > 1, "Expected multiple distinct facts across 50 draws");
    }
}