package com.ecotrack.ecotrack_backend.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Payload for POST /api/user-challenges/{id}/log-activity.
 *
 * amount is only read for DISTANCE/DURATION tracking types (e.g. "5.2" km,
 * "30" minutes). For COUNT/BOOLEAN challenges it is ignored entirely — the
 * server always logs exactly 1 and enforces the one-log-per-day cap, so a
 * client can't self-report an inflated amount for those types.
 */
public class ActivityLogRequest {

    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    public ActivityLogRequest() {}

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
