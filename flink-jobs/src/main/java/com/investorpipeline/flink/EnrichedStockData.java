package com.investorpipeline.flink;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Enhanced stock data model with calculated fields
 */
public class EnrichedStockData {
    private String symbol;
    private BigDecimal price;
    private Long volume;
    private String source;
    private Instant timestamp;
    private BigDecimal changePercent;
    private Long volumeChange;
    private Instant processedAt;
    
    // Constructors
    public EnrichedStockData() {}
    
    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    
    public Long getVolumeChange() { return volumeChange; }
    public void setVolumeChange(Long volumeChange) { this.volumeChange = volumeChange; }
    
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}