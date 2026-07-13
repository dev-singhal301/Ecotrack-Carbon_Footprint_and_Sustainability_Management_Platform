package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.CarbonEntryRequest;
import com.ecotrack.ecotrack_backend.dto.CarbonEntryResponse;
import com.ecotrack.ecotrack_backend.entity.CarbonCategory;
import com.ecotrack.ecotrack_backend.entity.CarbonEntry;
import com.ecotrack.ecotrack_backend.entity.EmissionFactor;
import com.ecotrack.ecotrack_backend.entity.User;
import com.ecotrack.ecotrack_backend.exception.CarbonEntryNotFoundException;
import com.ecotrack.ecotrack_backend.exception.InvalidCategoryException;
import com.ecotrack.ecotrack_backend.repository.CarbonEntryRepository;
import com.ecotrack.ecotrack_backend.repository.EmissionFactorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CarbonEntryService {

    private final CarbonEntryRepository carbonEntryRepository;
    private final EmissionFactorRepository emissionFactorRepository;

    // Constructor injection, matching Milestone 1 convention
    public CarbonEntryService(CarbonEntryRepository carbonEntryRepository,
                              EmissionFactorRepository emissionFactorRepository) {
        this.carbonEntryRepository = carbonEntryRepository;
        this.emissionFactorRepository = emissionFactorRepository;
    }

    // Converts the DTO's raw String into the CarbonCategory enum.
    // Dev's DTOs use String (not the enum), so this boundary conversion has to
    // happen somewhere — the service layer is the right place, keeping the
    // controller and DTOs unaware of the enum entirely.
    private CarbonCategory parseCategory(String rawCategory) {
        try {
            return CarbonCategory.valueOf(rawCategory.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCategoryException(
                    "Invalid category: " + rawCategory + ". Must be one of: TRANSPORTATION, ELECTRICITY, FOOD, WASTE, WATER, FUEL");
        }
    }

    public CarbonEntryResponse createEntry(CarbonEntryRequest request, User user) {
        CarbonCategory category = parseCategory(request.getCategory());

        EmissionFactor factor = emissionFactorRepository.findByCategory(category)
                .orElseThrow(() -> new InvalidCategoryException(
                        "No emission factor configured for category: " + category));

        CarbonEntry entry = new CarbonEntry();
        entry.setUser(user);
        entry.setEmissionFactor(factor);
        entry.setCategory(category);
        entry.setAmount(request.getAmount());
        entry.setUnit(request.getUnit());
        entry.setActivityDate(request.getActivityDate());
        // The core calculation: amount x factor_value
        entry.setCarbonEmission(request.getAmount().multiply(factor.getFactorValue()));

        CarbonEntry saved = carbonEntryRepository.save(entry);
        return toResponse(saved);
    }

    public List<CarbonEntryResponse> getHistory(Long userId, String rawCategory, LocalDate from, LocalDate to) {
        List<CarbonEntry> entries;

        if (rawCategory != null && from != null && to != null) {
            entries = carbonEntryRepository.findByUserIdAndCategoryAndActivityDateBetween(
                    userId, parseCategory(rawCategory), from, to);
        } else if (rawCategory != null) {
            entries = carbonEntryRepository.findByUserIdAndCategory(userId, parseCategory(rawCategory));
        } else if (from != null && to != null) {
            entries = carbonEntryRepository.findByUserIdAndActivityDateBetween(userId, from, to);
        } else {
            entries = carbonEntryRepository.findByUserId(userId);
        }

        return entries.stream().map(this::toResponse).toList();
    }

    public CarbonEntryResponse getEntryById(Long userId, Long entryId) {
        CarbonEntry entry = carbonEntryRepository.findById(entryId)
                .filter(e -> e.getUser().getId().equals(userId))
                .orElseThrow(() -> new CarbonEntryNotFoundException("Carbon entry not found: " + entryId));
        return toResponse(entry);
    }

    public void deleteEntry(Long userId, Long entryId) {
        CarbonEntry entry = carbonEntryRepository.findById(entryId)
                .filter(e -> e.getUser().getId().equals(userId))
                .orElseThrow(() -> new CarbonEntryNotFoundException("Carbon entry not found: " + entryId));
        carbonEntryRepository.delete(entry);
    }

    private CarbonEntryResponse toResponse(CarbonEntry entry) {
        return new CarbonEntryResponse(
                entry.getEntryId(),
                entry.getCategory().name(),
                entry.getAmount(),
                entry.getUnit(),
                entry.getCarbonEmission(),
                entry.getActivityDate()
        );
    }
}