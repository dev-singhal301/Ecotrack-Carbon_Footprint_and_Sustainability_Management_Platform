package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One logged activity event for an ACTIVITY_BASED UserChallenge — e.g. "5.2 km
 * cycled" or "+1 tree planted". Carbon-reduction challenges never create rows
 * here; their progress is derived directly from carbon_entries instead
 * (Challenge Module Design doc §6).
 *
 * amount's meaning depends on the challenge's TrackingType:
 *  - COUNT / BOOLEAN: always 1 (server-set, not client-supplied) and capped
 *    at one log per calendar day per UserChallenge — see
 *    UserChallengeService.logActivity for the anti-abuse check.
 *  - DISTANCE / DURATION: client-supplied amount, multiple logs per day are
 *    summed.
 */
@Entity
@Table(name = "challenge_activity_logs")
public class ChallengeActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_challenge_id", nullable = false)
    private UserChallenge userChallenge;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.logDate == null) {
            this.logDate = LocalDate.now();
        }
    }

    public ChallengeActivityLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserChallenge getUserChallenge() { return userChallenge; }
    public void setUserChallenge(UserChallenge userChallenge) { this.userChallenge = userChallenge; }

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
