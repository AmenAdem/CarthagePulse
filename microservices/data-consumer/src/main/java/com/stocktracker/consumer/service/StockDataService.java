package com.stocktracker.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktracker.consumer.entity.StockData;
import com.stocktracker.consumer.repository.StockDataRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling stock data operations including Kafka consumption,
 * database operations, and real-time WebSocket broadcasting
 */
@Service
@Transactional
public class StockDataService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StockDataRepository stockDataRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Metrics
    private final Counter messagesProcessedCounter;
    private final Counter messagesFailedCounter;
    private final Timer processingTimer;

    @Autowired
    public StockDataService(
            StockDataRepository stockDataRepository,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            MeterRegistry meterRegistry) {
        this.stockDataRepository = stockDataRepository;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        
        // Initialize metrics
        this.messagesProcessedCounter = Counter.builder("kafka.messages.processed")
                .description("Number of Kafka messages processed")
                .tag("service", "stock-data")
                .register(meterRegistry);
                
        this.messagesFailedCounter = Counter.builder("kafka.messages.failed")
                .description("Number of Kafka messages failed")
                .tag("service", "stock-data")
                .register(meterRegistry);
                
        this.processingTimer = Timer.builder("kafka.message.processing.time")
                .description("Time taken to process Kafka messages")
                .tag("service", "stock-data")
                .register(meterRegistry);
    }

    /**
     * Kafka listener for stock data topic
     */
    @KafkaListener(
            topics = "${app.kafka.topics.stock-data}",
            containerFactory = "stockDataKafkaListenerContainerFactory",
            groupId = "${spring.kafka.consumer.group-id}-stock"
    )
    public void handleStockData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        
        try {
            logger.debug("Received stock data message from topic: {} partition: {} offset: {}", 
                        topic, partition, offset);
            
            // Parse the JSON message
            Map<String, Object> stockDataMap = objectMapper.readValue(message, Map.class);
            
            // Convert to entity and save
            StockData stockData = convertToStockData(stockDataMap);
            StockData savedData = stockDataRepository.save(stockData);
            
            // Broadcast to WebSocket subscribers
            broadcastStockUpdate(savedData);
            
            // Acknowledge message processing
            acknowledgment.acknowledge();
            messagesProcessedCounter.increment();
            
            logger.info("Successfully processed stock data for symbol: {}", stockData.getSymbol());
            
        } catch (Exception e) {
            logger.error("Error processing stock data message: {}", message, e);
            messagesFailedCounter.increment();
            
            // Don't acknowledge - let Kafka retry
            // acknowledgment.acknowledge(); 
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Kafka listener for news data topic
     */
    @KafkaListener(
            topics = "${app.kafka.topics.news-data}",
            containerFactory = "newsDataKafkaListenerContainerFactory",
            groupId = "${spring.kafka.consumer.group-id}-news"
    )
    public void handleNewsData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        try {
            logger.debug("Received news data from topic: {}", topic);
            
            // Parse and process news data
            Map<String, Object> newsDataMap = objectMapper.readValue(message, Map.class);
            
            // Broadcast news update to WebSocket subscribers
            messagingTemplate.convertAndSend("/topic/news", newsDataMap);
            
            acknowledgment.acknowledge();
            logger.debug("Successfully processed news data");
            
        } catch (Exception e) {
            logger.error("Error processing news data message: {}", message, e);
        }
    }

    /**
     * Kafka listener for economic data topic
     */
    @KafkaListener(
            topics = "${app.kafka.topics.economic-data}",
            containerFactory = "economicDataKafkaListenerContainerFactory",
            groupId = "${spring.kafka.consumer.group-id}-economic"
    )
    public void handleEconomicData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        
        try {
            logger.debug("Received economic data from topic: {}", topic);
            
            // Parse and process economic data
            Map<String, Object> economicDataMap = objectMapper.readValue(message, Map.class);
            
            // Broadcast economic update to WebSocket subscribers
            messagingTemplate.convertAndSend("/topic/economic", economicDataMap);
            
            acknowledgment.acknowledge();
            logger.debug("Successfully processed economic data");
            
        } catch (Exception e) {
            logger.error("Error processing economic data message: {}", message, e);
        }
    }

    /**
     * Convert map to StockData entity
     */
    private StockData convertToStockData(Map<String, Object> dataMap) {
        StockData stockData = new StockData();
        
        stockData.setSymbol((String) dataMap.get("symbol"));
        stockData.setPrice(new BigDecimal(dataMap.get("price").toString()));
        stockData.setVolume(Long.valueOf(dataMap.get("volume").toString()));
        stockData.setSource((String) dataMap.get("source"));
        
        // Handle timestamp
        if (dataMap.get("timestamp") instanceof Number) {
            long timestamp = ((Number) dataMap.get("timestamp")).longValue();
            stockData.setTimestamp(LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.UTC));
        } else {
            stockData.setTimestamp(LocalDateTime.now());
        }
        
        // Handle optional fields
        if (dataMap.containsKey("change") && dataMap.get("change") != null) {
            stockData.setChange(new BigDecimal(dataMap.get("change").toString()));
        }
        
        if (dataMap.containsKey("change_percent") && dataMap.get("change_percent") != null) {
            stockData.setChangePercent(new BigDecimal(dataMap.get("change_percent").toString()));
        }
        
        if (dataMap.containsKey("market_cap") && dataMap.get("market_cap") != null) {
            stockData.setMarketCap(new BigDecimal(dataMap.get("market_cap").toString()));
        }
        
        if (dataMap.containsKey("pe_ratio") && dataMap.get("pe_ratio") != null) {
            stockData.setPeRatio(new BigDecimal(dataMap.get("pe_ratio").toString()));
        }
        
        return stockData;
    }

    /**
     * Broadcast stock update via WebSocket
     */
    private void broadcastStockUpdate(StockData stockData) {
        try {
            messagingTemplate.convertAndSend("/topic/stocks/" + stockData.getSymbol(), stockData);
            messagingTemplate.convertAndSend("/topic/stocks", stockData);
        } catch (Exception e) {
            logger.error("Error broadcasting stock update for symbol: {}", stockData.getSymbol(), e);
        }
    }

    /**
     * Get latest stock data for a symbol
     */
    @Cacheable(value = "latestStockData", key = "#symbol")
    public Optional<StockData> getLatestStockData(String symbol) {
        return stockDataRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    /**
     * Get historical stock data with pagination
     */
    public Page<StockData> getHistoricalData(String symbol, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return stockDataRepository.findBySymbolOrderByTimestampDesc(symbol, pageable);
    }

    /**
     * Get stock data within time range
     */
    public List<StockData> getStockDataInRange(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        return stockDataRepository.findBySymbolAndTimestampBetween(symbol, startTime, endTime);
    }

    /**
     * Get latest data for multiple symbols
     */
    public List<StockData> getLatestDataForSymbols(List<String> symbols) {
        return stockDataRepository.findLatestBySymbols(symbols);
    }

    /**
     * Get trending stocks by volume
     */
    public List<Object[]> getTrendingStocks(int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Pageable pageable = PageRequest.of(0, limit);
        return stockDataRepository.findTrendingByVolume(since, pageable);
    }

    /**
     * Get stocks with significant price changes
     */
    public List<StockData> getStocksWithSignificantChanges(BigDecimal threshold, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Pageable pageable = PageRequest.of(0, limit);
        return stockDataRepository.findStocksWithSignificantChanges(threshold, since, pageable);
    }

    /**
     * Get all available symbols
     */
    @Cacheable(value = "availableSymbols")
    public List<String> getAvailableSymbols() {
        return stockDataRepository.findDistinctSymbols();
    }

    /**
     * Save stock data manually (for testing or external APIs)
     */
    public StockData saveStockData(StockData stockData) {
        StockData savedData = stockDataRepository.save(stockData);
        broadcastStockUpdate(savedData);
        return savedData;
    }

    /**
     * Async method to save multiple stock data points
     */
    public CompletableFuture<List<StockData>> saveStockDataBatch(List<StockData> stockDataList) {
        return CompletableFuture.supplyAsync(() -> {
            List<StockData> savedData = stockDataRepository.saveAll(stockDataList);
            
            // Broadcast updates for each saved item
            savedData.forEach(this::broadcastStockUpdate);
            
            return savedData;
        });
    }

    /**
     * Scheduled task to clean up old data
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanupOldData() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(90); // Keep 90 days
            stockDataRepository.deleteOldData(cutoffTime);
            logger.info("Cleaned up stock data older than {}", cutoffTime);
        } catch (Exception e) {
            logger.error("Error during data cleanup", e);
        }
    }

    /**
     * Get database statistics
     */
    public Map<String, Object> getDatabaseStats() {
        List<String> symbols = getAvailableSymbols();
        
        return Map.of(
                "totalSymbols", symbols.size(),
                "totalRecords", stockDataRepository.count(),
                "availableSymbols", symbols
        );
    }
}