package com.stocktracker.consumer.repository;

import com.stocktracker.consumer.entity.StockData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Stock Data with optimized queries for time-series data
 */
@Repository
public interface StockDataRepository extends JpaRepository<StockData, Long> {

    /**
     * Find latest stock data for a symbol
     */
    Optional<StockData> findTopBySymbolOrderByTimestampDesc(String symbol);

    /**
     * Find all stock data for a symbol within time range
     */
    @Query("SELECT s FROM StockData s WHERE s.symbol = :symbol AND s.timestamp BETWEEN :startTime AND :endTime ORDER BY s.timestamp DESC")
    List<StockData> findBySymbolAndTimestampBetween(
        @Param("symbol") String symbol, 
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find latest stock data for multiple symbols
     */
    @Query("SELECT s FROM StockData s WHERE s.symbol IN :symbols AND s.timestamp = " +
           "(SELECT MAX(s2.timestamp) FROM StockData s2 WHERE s2.symbol = s.symbol)")
    List<StockData> findLatestBySymbols(@Param("symbols") List<String> symbols);

    /**
     * Find stock data with pagination
     */
    Page<StockData> findBySymbolOrderByTimestampDesc(String symbol, Pageable pageable);

    /**
     * Find stock data by price range
     */
    @Query("SELECT s FROM StockData s WHERE s.symbol = :symbol AND s.price BETWEEN :minPrice AND :maxPrice ORDER BY s.timestamp DESC")
    List<StockData> findBySymbolAndPriceBetween(
        @Param("symbol") String symbol,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice
    );

    /**
     * Get stock statistics for a time period
     */
    @Query("SELECT s.symbol as symbol, " +
           "AVG(s.price) as avgPrice, " +
           "MIN(s.price) as minPrice, " +
           "MAX(s.price) as maxPrice, " +
           "SUM(s.volume) as totalVolume, " +
           "COUNT(*) as dataPoints " +
           "FROM StockData s " +
           "WHERE s.symbol = :symbol AND s.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY s.symbol")
    Object[] getStockStatistics(
        @Param("symbol") String symbol,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find trending stocks by volume
     */
    @Query("SELECT s.symbol, SUM(s.volume) as totalVolume " +
           "FROM StockData s " +
           "WHERE s.timestamp >= :since " +
           "GROUP BY s.symbol " +
           "ORDER BY totalVolume DESC")
    List<Object[]> findTrendingByVolume(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find stocks with significant price changes
     */
    @Query("SELECT s FROM StockData s WHERE s.changePercent IS NOT NULL AND " +
           "ABS(s.changePercent) >= :threshold AND s.timestamp >= :since " +
           "ORDER BY ABS(s.changePercent) DESC")
    List<StockData> findStocksWithSignificantChanges(
        @Param("threshold") BigDecimal threshold,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    /**
     * Delete old data for cleanup
     */
    @Query("DELETE FROM StockData s WHERE s.timestamp < :cutoffTime")
    void deleteOldData(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count data points by symbol
     */
    @Query("SELECT COUNT(*) FROM StockData s WHERE s.symbol = :symbol")
    Long countBySymbol(@Param("symbol") String symbol);

    /**
     * Find distinct symbols
     */
    @Query("SELECT DISTINCT s.symbol FROM StockData s ORDER BY s.symbol")
    List<String> findDistinctSymbols();

    /**
     * Get OHLC data for charting
     */
    @Query("SELECT DATE(s.timestamp) as date, " +
           "MIN(s.price) as low, " +
           "MAX(s.price) as high, " +
           "FIRST_VALUE(s.price) OVER (PARTITION BY DATE(s.timestamp) ORDER BY s.timestamp) as open, " +
           "LAST_VALUE(s.price) OVER (PARTITION BY DATE(s.timestamp) ORDER BY s.timestamp) as close, " +
           "SUM(s.volume) as volume " +
           "FROM StockData s " +
           "WHERE s.symbol = :symbol AND s.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY DATE(s.timestamp) " +
           "ORDER BY date")
    List<Object[]> getOHLCData(
        @Param("symbol") String symbol,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}