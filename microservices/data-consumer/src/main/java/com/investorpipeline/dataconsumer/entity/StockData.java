package com.investorpipeline.dataconsumer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_data", indexes = {
    @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_source", columnList = "source")
})
public class StockData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;
    
    @NotNull
    @Positive
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @NotNull
    @Column(name = "volume", nullable = false)
    private Long volume;
    
    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @NotNull
    @Column(name = "source", nullable = false, length = 50)
    private String source;
    
    @Column(name = "change_percent", precision = 5, scale = 2)
    private BigDecimal changePercent;
    
    @Column(name = "market_cap", precision = 15, scale = 2)
    private BigDecimal marketCap;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors
    public StockData() {}
    
    public StockData(String symbol, BigDecimal price, Long volume, 
                    LocalDateTime timestamp, String source) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.source = source;
        this.createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    
    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}