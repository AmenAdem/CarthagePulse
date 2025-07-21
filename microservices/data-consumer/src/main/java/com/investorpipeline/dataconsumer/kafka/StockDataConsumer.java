package com.investorpipeline.dataconsumer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investorpipeline.dataconsumer.entity.StockData;
import com.investorpipeline.dataconsumer.service.StockDataService;
import com.investorpipeline.dataconsumer.websocket.StockDataWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class StockDataConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(StockDataConsumer.class);
    
    @Autowired
    private StockDataService stockDataService;
    
    @Autowired
    private StockDataWebSocketHandler webSocketHandler;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = "${app.kafka.stock-data-topic}",
        groupId = "${app.kafka.consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeStockData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String key,
            Acknowledgment acknowledgment) {
        
        try {
            logger.info("Received message from topic: {}, partition: {}, offset: {}, key: {}", 
                       topic, partition, offset, key);
            
            // Parse the message
            @SuppressWarnings("unchecked")
            Map<String, Object> stockDataMap = objectMapper.readValue(message, Map.class);
            
            // Convert to StockData entity
            StockData stockData = convertToStockData(stockDataMap);
            
            // Save to database
            StockData savedData = stockDataService.saveStockData(stockData);
            
            // Send real-time update via WebSocket
            webSocketHandler.broadcastStockUpdate(savedData);
            
            // Acknowledge message processing
            acknowledgment.acknowledge();
            
            logger.info("Successfully processed stock data for symbol: {}", stockData.getSymbol());
            
        } catch (Exception e) {
            logger.error("Error processing stock data message: {}", message, e);
            // In production, implement dead letter queue or retry mechanism
            acknowledgment.acknowledge(); // For now, acknowledge to avoid infinite retry
        }
    }
    
    private StockData convertToStockData(Map<String, Object> data) {
        StockData stockData = new StockData();
        
        stockData.setSymbol((String) data.get("symbol"));
        stockData.setPrice(new BigDecimal(data.get("price").toString()));
        stockData.setVolume(Long.valueOf(data.get("volume").toString()));
        stockData.setSource((String) data.get("source"));
        
        // Parse timestamp
        String timestampStr = (String) data.get("timestamp");
        if (timestampStr != null) {
            stockData.setTimestamp(LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            stockData.setTimestamp(LocalDateTime.now());
        }
        
        // Optional fields
        if (data.get("change_percent") != null) {
            stockData.setChangePercent(new BigDecimal(data.get("change_percent").toString()));
        }
        
        if (data.get("market_cap") != null) {
            stockData.setMarketCap(new BigDecimal(data.get("market_cap").toString()));
        }
        
        return stockData;
    }
}