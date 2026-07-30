package com.ecotrack.ecotrack_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "xp_transactions")
public class XpTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "xp_transaction_id")
    private Long xpTransactionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "xp_amount", nullable = false)
    private Integer xpAmount;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public XpTransaction() {}

    public XpTransaction(Long userId, Integer xpAmount, String sourceType, Long sourceId) {
        this.userId = userId;
        this.xpAmount = xpAmount;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getXpTransactionId() {
        return xpTransactionId;
    }

    public void setXpTransactionId(Long xpTransactionId) {
        this.xpTransactionId = xpTransactionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getXpAmount() {
        return xpAmount;
    }

    public void setXpAmount(Integer xpAmount) {
        this.xpAmount = xpAmount;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}