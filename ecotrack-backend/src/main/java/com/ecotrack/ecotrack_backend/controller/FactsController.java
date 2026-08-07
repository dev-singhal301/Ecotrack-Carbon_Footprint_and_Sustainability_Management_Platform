package com.ecotrack.ecotrack_backend.controller;

import com.ecotrack.ecotrack_backend.dto.FactResponse;
import com.ecotrack.ecotrack_backend.service.FactsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/facts")
public class FactsController {

    private final FactsService factsService;

    public FactsController(FactsService factsService) {
        this.factsService = factsService;
    }

    // Non-personalized (Decision H) — no Authentication/user lookup needed,
    // unlike RecommendationController. Still sits behind SecurityConfig's
    // existing authenticated-route rule; it just doesn't scope by user.
    @GetMapping("/random")
    public ResponseEntity<FactResponse> getRandomFact() {
        return ResponseEntity.ok(factsService.getRandomFact());
    }
}