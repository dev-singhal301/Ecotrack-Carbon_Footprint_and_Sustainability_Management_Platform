package com.ecotrack.ecotrack_backend.dto;

import java.math.BigDecimal;

public class FootprintTrendResponse {
    private BigDecimal currentTotal;
    private BigDecimal previousTotal;
    private BigDecimal percentChange; // null when there's no previous-period data
    private boolean hasPreviousData;

    public FootprintTrendResponse() {}
    public FootprintTrendResponse(BigDecimal currentTotal, BigDecimal previousTotal,
                                   BigDecimal percentChange, boolean hasPreviousData) {
        this.currentTotal = currentTotal;
        this.previousTotal = previousTotal;
        this.percentChange = percentChange;
        this.hasPreviousData = hasPreviousData;
    }
    public BigDecimal getCurrentTotal() { return currentTotal; }
    public void setCurrentTotal(BigDecimal currentTotal) { this.currentTotal = currentTotal; }
    public BigDecimal getPreviousTotal() { return previousTotal; }
    public void setPreviousTotal(BigDecimal previousTotal) { this.previousTotal = previousTotal; }
    public BigDecimal getPercentChange() { return percentChange; }
    public void setPercentChange(BigDecimal percentChange) { this.percentChange = percentChange; }
    public boolean isHasPreviousData() { return hasPreviousData; }
    public void setHasPreviousData(boolean hasPreviousData) { this.hasPreviousData = hasPreviousData; }
}