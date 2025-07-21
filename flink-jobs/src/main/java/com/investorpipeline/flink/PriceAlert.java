package com.investorpipeline.flink;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Price alert model for significant price changes
 */
public class PriceAlert {
    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal changePercent;
    private Long volume;
    private Instant timestamp;
    private String alertType; // PRICE_SURGE, PRICE_DROP
    private String severity; // LOW, MEDIUM, HIGH
    
    // Constructors
    public PriceAlert() {}
    
    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}