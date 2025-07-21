package com.investorpipeline.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;

/**
 * Enhanced Flink job for real-time stock data processing
 * Features:
 * - Real-time data enrichment and transformation
 * - Windowing and aggregations
 * - State management for price tracking
 * - Alert generation for significant price changes
 */
public class StockDataProcessingJob {
    
    private static final Logger logger = LoggerFactory.getLogger(StockDataProcessingJob.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        
        // Set up execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(30000); // checkpoint every 30 seconds
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        env.getCheckpointConfig().setCheckpointTimeout(60000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        
        // Configure Kafka source
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("stock-data-raw")
                .setGroupId("flink-processing-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
        
        // Create data stream from Kafka
        DataStream<String> rawStockData = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
        
        // Parse and enrich stock data
        DataStream<EnrichedStockData> enrichedData = rawStockData
                .map(new StockDataParser())
                .filter(data -> data != null)
                .keyBy(EnrichedStockData::getSymbol)
                .process(new StockDataEnricher());
        
        // Generate price alerts for significant changes
        DataStream<PriceAlert> alerts = enrichedData
                .keyBy(EnrichedStockData::getSymbol)
                .process(new PriceAlertGenerator());
        
        // Calculate moving averages using windowing
        DataStream<MovingAverageData> movingAverages = enrichedData
                .keyBy(EnrichedStockData::getSymbol)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .process(new MovingAverageCalculator());
        
        // Configure Kafka sink for processed data
        KafkaSink<String> processedDataSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("stock-data-processed")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();
        
        // Configure Kafka sink for alerts
        KafkaSink<String> alertsSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("price-alerts")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();
        
        // Configure Kafka sink for moving averages
        KafkaSink<String> movingAveragesSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("moving-averages")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();
        
        // Send processed data to Kafka
        enrichedData
                .map(data -> objectMapper.writeValueAsString(data))
                .sinkTo(processedDataSink);
        
        // Send alerts to Kafka
        alerts
                .map(alert -> objectMapper.writeValueAsString(alert))
                .sinkTo(alertsSink);
        
        // Send moving averages to Kafka
        movingAverages
                .map(ma -> objectMapper.writeValueAsString(ma))
                .sinkTo(movingAveragesSink);
        
        // Execute the job
        env.execute("Stock Data Processing Job");
    }
    
    /**
     * Parser for raw stock data from Kafka
     */
    public static class StockDataParser implements MapFunction<String, EnrichedStockData> {
        @Override
        public EnrichedStockData map(String jsonString) throws Exception {
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                
                EnrichedStockData data = new EnrichedStockData();
                data.setSymbol(jsonNode.get("symbol").asText());
                data.setPrice(new BigDecimal(jsonNode.get("price").asText()));
                data.setVolume(jsonNode.get("volume").asLong());
                data.setSource(jsonNode.get("source").asText());
                data.setTimestamp(Instant.parse(jsonNode.get("timestamp").asText()));
                
                if (jsonNode.has("change_percent")) {
                    data.setChangePercent(new BigDecimal(jsonNode.get("change_percent").asText()));
                }
                
                return data;
            } catch (Exception e) {
                logger.error("Failed to parse stock data: {}", jsonString, e);
                return null;
            }
        }
    }
    
    /**
     * Enriches stock data with additional calculated fields
     */
    public static class StockDataEnricher extends KeyedProcessFunction<String, EnrichedStockData, EnrichedStockData> {
        
        private transient ValueState<EnrichedStockData> lastDataState;
        
        @Override
        public void open(Configuration parameters) {
            ValueStateDescriptor<EnrichedStockData> descriptor = new ValueStateDescriptor<>(
                    "lastData",
                    TypeInformation.of(EnrichedStockData.class)
            );
            lastDataState = getRuntimeContext().getState(descriptor);
        }
        
        @Override
        public void processElement(EnrichedStockData current, Context ctx, Collector<EnrichedStockData> out) throws Exception {
            EnrichedStockData lastData = lastDataState.value();
            
            if (lastData != null) {
                // Calculate price change percentage
                BigDecimal priceChange = current.getPrice().subtract(lastData.getPrice());
                BigDecimal changePercent = priceChange
                        .divide(lastData.getPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                current.setChangePercent(changePercent);
                
                // Calculate volume change
                long volumeChange = current.getVolume() - lastData.getVolume();
                current.setVolumeChange(volumeChange);
            }
            
            // Add processing timestamp
            current.setProcessedAt(Instant.now());
            
            // Update state
            lastDataState.update(current);
            
            out.collect(current);
        }
    }
    
    /**
     * Generates alerts for significant price changes
     */
    public static class PriceAlertGenerator extends KeyedProcessFunction<String, EnrichedStockData, PriceAlert> {
        
        private static final BigDecimal ALERT_THRESHOLD = new BigDecimal("5.0"); // 5% change
        
        @Override
        public void processElement(EnrichedStockData data, Context ctx, Collector<PriceAlert> out) throws Exception {
            if (data.getChangePercent() != null && 
                data.getChangePercent().abs().compareTo(ALERT_THRESHOLD) > 0) {
                
                PriceAlert alert = new PriceAlert();
                alert.setSymbol(data.getSymbol());
                alert.setCurrentPrice(data.getPrice());
                alert.setChangePercent(data.getChangePercent());
                alert.setVolume(data.getVolume());
                alert.setTimestamp(data.getTimestamp());
                alert.setAlertType(data.getChangePercent().compareTo(BigDecimal.ZERO) > 0 ? "PRICE_SURGE" : "PRICE_DROP");
                alert.setSeverity(data.getChangePercent().abs().compareTo(new BigDecimal("10.0")) > 0 ? "HIGH" : "MEDIUM");
                
                out.collect(alert);
            }
        }
    }
    
    /**
     * Calculates moving averages using windowing
     */
    public static class MovingAverageCalculator extends ProcessWindowFunction<EnrichedStockData, MovingAverageData, String, TimeWindow> {
        
        @Override
        public void process(String symbol, Context context, Iterable<EnrichedStockData> elements, Collector<MovingAverageData> out) {
            Iterator<EnrichedStockData> iterator = elements.iterator();
            
            if (!iterator.hasNext()) {
                return;
            }
            
            BigDecimal sum = BigDecimal.ZERO;
            long totalVolume = 0;
            int count = 0;
            
            while (iterator.hasNext()) {
                EnrichedStockData data = iterator.next();
                sum = sum.add(data.getPrice());
                totalVolume += data.getVolume();
                count++;
            }
            
            if (count > 0) {
                MovingAverageData ma = new MovingAverageData();
                ma.setSymbol(symbol);
                ma.setAveragePrice(sum.divide(new BigDecimal(count), 4, RoundingMode.HALF_UP));
                ma.setAverageVolume(totalVolume / count);
                ma.setDataPoints(count);
                ma.setWindowStart(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(context.window().getStart()), 
                        ZoneOffset.UTC));
                ma.setWindowEnd(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(context.window().getEnd()), 
                        ZoneOffset.UTC));
                ma.setCalculatedAt(Instant.now());
                
                out.collect(ma);
            }
        }
    }
}