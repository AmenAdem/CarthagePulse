package com.investorpipeline.flink;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Moving average data model for windowed calculations
 */
public class MovingAverageData {
    private String symbol;
    private BigDecimal averagePrice;
    private Long averageVolume;
    private Integer dataPoints;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Instant calculatedAt;
    
    // Constructors
    public MovingAverageData() {}
    
    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
    
    public Long getAverageVolume() { return averageVolume; }
    public void setAverageVolume(Long averageVolume) { this.averageVolume = averageVolume; }
    
    public Integer getDataPoints() { return dataPoints; }
    public void setDataPoints(Integer dataPoints) { this.dataPoints = dataPoints; }
    
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    
    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }
}