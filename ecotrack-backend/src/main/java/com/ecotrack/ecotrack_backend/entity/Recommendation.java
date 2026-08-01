package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private CarbonCategory category;

    @Column(name = "suggestion_text", nullable = false, length = 500)
    private String suggestionText;

    @Column(name = "impact_estimate", nullable = false)
    private BigDecimal impactEstimate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Recommendation() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public CarbonCategory getCategory() {
        return category;
    }

    public void setCategory(CarbonCategory category) {
        this.category = category;
    }

    public String getSuggestionText() {
        return suggestionText;
    }

    public void setSuggestionText(String suggestionText) {
        this.suggestionText = suggestionText;
    }

    public BigDecimal getImpactEstimate() {
        return impactEstimate;
    }

    public void setImpactEstimate(BigDecimal impactEstimate) {
        this.impactEstimate = impactEstimate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}