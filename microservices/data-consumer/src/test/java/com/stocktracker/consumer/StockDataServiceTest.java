package com.stocktracker.consumer.service;

import com.stocktracker.consumer.entity.StockData;
import com.stocktracker.consumer.repository.StockDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StockDataService
 */
@ExtendWith(MockitoExtension.class)
class StockDataServiceTest {

    @Mock
    private StockDataRepository stockDataRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter messagesProcessedCounter;

    @Mock
    private Counter messagesFailedCounter;

    @Mock
    private Timer processingTimer;

    @Mock
    private Timer.Sample timerSample;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private StockDataService stockDataService;

    @BeforeEach
    void setUp() {
        // Setup meter registry mocks
        when(meterRegistry.counter(anyString())).thenReturn(messagesProcessedCounter);
        when(meterRegistry.timer(anyString())).thenReturn(processingTimer);
        when(Timer.start()).thenReturn(timerSample);
        
        // Initialize service manually since @InjectMocks can't handle constructor injection properly
        stockDataService = new StockDataService(
            stockDataRepository, 
            objectMapper, 
            messagingTemplate, 
            meterRegistry
        );
    }

    @Test
    void testGetLatestStockData() {
        // Arrange
        String symbol = "AAPL";
        StockData expectedData = createTestStockData(symbol);
        when(stockDataRepository.findTopBySymbolOrderByTimestampDesc(symbol))
            .thenReturn(Optional.of(expectedData));

        // Act
        Optional<StockData> result = stockDataService.getLatestStockData(symbol);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(symbol, result.get().getSymbol());
        assertEquals(expectedData.getPrice(), result.get().getPrice());
        verify(stockDataRepository).findTopBySymbolOrderByTimestampDesc(symbol);
    }

    @Test
    void testGetLatestStockDataNotFound() {
        // Arrange
        String symbol = "UNKNOWN";
        when(stockDataRepository.findTopBySymbolOrderByTimestampDesc(symbol))
            .thenReturn(Optional.empty());

        // Act
        Optional<StockData> result = stockDataService.getLatestStockData(symbol);

        // Assert
        assertFalse(result.isPresent());
        verify(stockDataRepository).findTopBySymbolOrderByTimestampDesc(symbol);
    }

    @Test
    void testGetStockDataInRange() {
        // Arrange
        String symbol = "AAPL";
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now();
        List<StockData> expectedData = Arrays.asList(
            createTestStockData(symbol),
            createTestStockData(symbol)
        );
        
        when(stockDataRepository.findBySymbolAndTimestampBetween(symbol, startTime, endTime))
            .thenReturn(expectedData);

        // Act
        List<StockData> result = stockDataService.getStockDataInRange(symbol, startTime, endTime);

        // Assert
        assertEquals(2, result.size());
        assertEquals(symbol, result.get(0).getSymbol());
        verify(stockDataRepository).findBySymbolAndTimestampBetween(symbol, startTime, endTime);
    }

    @Test
    void testSaveStockData() {
        // Arrange
        StockData stockData = createTestStockData("AAPL");
        when(stockDataRepository.save(any(StockData.class))).thenReturn(stockData);

        // Act
        StockData result = stockDataService.saveStockData(stockData);

        // Assert
        assertNotNull(result);
        assertEquals(stockData.getSymbol(), result.getSymbol());
        verify(stockDataRepository).save(stockData);
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(StockData.class));
    }

    @Test
    void testHandleStockDataSuccess() throws Exception {
        // Arrange
        String message = "{\"symbol\":\"AAPL\",\"price\":150.50,\"volume\":1000000,\"timestamp\":1640995200,\"source\":\"test\"}";
        Map<String, Object> stockDataMap = new HashMap<>();
        stockDataMap.put("symbol", "AAPL");
        stockDataMap.put("price", 150.50);
        stockDataMap.put("volume", 1000000);
        stockDataMap.put("timestamp", 1640995200);
        stockDataMap.put("source", "test");

        StockData stockData = createTestStockData("AAPL");
        
        when(objectMapper.readValue(eq(message), eq(Map.class))).thenReturn(stockDataMap);
        when(stockDataRepository.save(any(StockData.class))).thenReturn(stockData);

        // Act
        stockDataService.handleStockData(message, "stock-data", 0, 0L, acknowledgment);

        // Assert
        verify(objectMapper).readValue(message, Map.class);
        verify(stockDataRepository).save(any(StockData.class));
        verify(acknowledgment).acknowledge();
        verify(messagesProcessedCounter).increment();
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(StockData.class));
    }

    @Test
    void testHandleStockDataFailure() throws Exception {
        // Arrange
        String message = "invalid json";
        when(objectMapper.readValue(eq(message), eq(Map.class)))
            .thenThrow(new RuntimeException("JSON parsing error"));

        // Act
        stockDataService.handleStockData(message, "stock-data", 0, 0L, acknowledgment);

        // Assert
        verify(objectMapper).readValue(message, Map.class);
        verify(stockDataRepository, never()).save(any(StockData.class));
        verify(acknowledgment, never()).acknowledge();
        verify(messagesFailedCounter).increment();
    }

    @Test
    void testGetTrendingStocks() {
        // Arrange
        int limit = 5;
        List<Object[]> expectedData = Arrays.asList(
            new Object[]{"AAPL", 10000000L},
            new Object[]{"GOOGL", 8000000L}
        );
        
        when(stockDataRepository.findTrendingByVolume(any(LocalDateTime.class), any()))
            .thenReturn(expectedData);

        // Act
        List<Object[]> result = stockDataService.getTrendingStocks(limit);

        // Assert
        assertEquals(2, result.size());
        assertEquals("AAPL", result.get(0)[0]);
        assertEquals(10000000L, result.get(0)[1]);
        verify(stockDataRepository).findTrendingByVolume(any(LocalDateTime.class), any());
    }

    @Test
    void testGetStocksWithSignificantChanges() {
        // Arrange
        BigDecimal threshold = new BigDecimal("5.0");
        int limit = 10;
        List<StockData> expectedData = Arrays.asList(
            createTestStockData("AAPL"),
            createTestStockData("GOOGL")
        );
        
        when(stockDataRepository.findStocksWithSignificantChanges(any(BigDecimal.class), any(LocalDateTime.class), any()))
            .thenReturn(expectedData);

        // Act
        List<StockData> result = stockDataService.getStocksWithSignificantChanges(threshold, limit);

        // Assert
        assertEquals(2, result.size());
        verify(stockDataRepository).findStocksWithSignificantChanges(any(BigDecimal.class), any(LocalDateTime.class), any());
    }

    @Test
    void testGetDatabaseStats() {
        // Arrange
        List<String> symbols = Arrays.asList("AAPL", "GOOGL", "MSFT");
        long totalRecords = 1000L;
        
        when(stockDataRepository.findDistinctSymbols()).thenReturn(symbols);
        when(stockDataRepository.count()).thenReturn(totalRecords);

        // Act
        Map<String, Object> stats = stockDataService.getDatabaseStats();

        // Assert
        assertEquals(3, stats.get("totalSymbols"));
        assertEquals(1000L, stats.get("totalRecords"));
        assertEquals(symbols, stats.get("availableSymbols"));
        verify(stockDataRepository).findDistinctSymbols();
        verify(stockDataRepository).count();
    }

    private StockData createTestStockData(String symbol) {
        StockData stockData = new StockData();
        stockData.setId(1L);
        stockData.setSymbol(symbol);
        stockData.setPrice(new BigDecimal("150.50"));
        stockData.setVolume(1000000L);
        stockData.setTimestamp(LocalDateTime.now());
        stockData.setChange(new BigDecimal("2.50"));
        stockData.setChangePercent(new BigDecimal("1.69"));
        stockData.setSource("test");
        return stockData;
    }
}