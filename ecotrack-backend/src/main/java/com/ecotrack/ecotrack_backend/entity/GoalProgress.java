package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "goal_progress")
public class GoalProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "progress_id")
    private Long progressId;

    @ManyToOne
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(name = "current_value")
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "completion_percentage")
    private BigDecimal completionPercentage = BigDecimal.ZERO;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public GoalProgress() {}

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getProgressId() { return progressId; }
    public void setProgressId(Long progressId) { this.progressId = progressId; }

    public Goal getGoal() { return goal; }
    public void setGoal(Goal goal) { this.goal = goal; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public BigDecimal getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(BigDecimal completionPercentage) { this.completionPercentage = completionPercentage; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}