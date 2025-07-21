package com.stocktracker.consumer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stock Data Entity for time-series storage
 */
@Entity
@Table(name = "stock_data", indexes = {
    @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_symbol", columnList = "symbol")
})
public class StockData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @PositiveOrZero
    @Column(nullable = false)
    private Long volume;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(precision = 19, scale = 4)
    private BigDecimal change;

    @Column(name = "change_percent", precision = 10, scale = 4)
    private BigDecimal changePercent;

    @Column(name = "market_cap", precision = 19, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "pe_ratio", precision = 10, scale = 2)
    private BigDecimal peRatio;

    @NotNull
    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column(name = "fifty_two_week_high", precision = 19, scale = 4)
    private BigDecimal fiftyTwoWeekHigh;

    @Column(name = "fifty_two_week_low", precision = 19, scale = 4)
    private BigDecimal fiftyTwoWeekLow;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public StockData() {}

    public StockData(String symbol, BigDecimal price, Long volume, LocalDateTime timestamp, String source) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.source = source;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getChange() {
        return change;
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(BigDecimal marketCap) {
        this.marketCap = marketCap;
    }

    public BigDecimal getPeRatio() {
        return peRatio;
    }

    public void setPeRatio(BigDecimal peRatio) {
        this.peRatio = peRatio;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public BigDecimal getDayHigh() {
        return dayHigh;
    }

    public void setDayHigh(BigDecimal dayHigh) {
        this.dayHigh = dayHigh;
    }

    public BigDecimal getDayLow() {
        return dayLow;
    }

    public void setDayLow(BigDecimal dayLow) {
        this.dayLow = dayLow;
    }

    public BigDecimal getFiftyTwoWeekHigh() {
        return fiftyTwoWeekHigh;
    }

    public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) {
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
    }

    public BigDecimal getFiftyTwoWeekLow() {
        return fiftyTwoWeekLow;
    }

    public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) {
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "StockData{" +
                "id=" + id +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", volume=" + volume +
                ", timestamp=" + timestamp +
                ", change=" + change +
                ", changePercent=" + changePercent +
                ", source='" + source + '\'' +
                '}';
    }
}