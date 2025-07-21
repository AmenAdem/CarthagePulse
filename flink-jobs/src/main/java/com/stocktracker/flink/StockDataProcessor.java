package com.stocktracker.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Flink job for processing stock data streams
 * Performs real-time calculations, aggregations, and alerts
 */
public class StockDataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockDataProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String INPUT_TOPIC = "stock-data";
    private static final String OUTPUT_TOPIC_ALERTS = "stock-alerts";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/stocktracker";
    private static final String JDBC_USERNAME = "stocktracker";
    private static final String JDBC_PASSWORD = "password";

    public static class StockRecord {
        public String symbol;
        public BigDecimal price;
        public Long volume;
        public LocalDateTime timestamp;
        public BigDecimal change;
        public BigDecimal changePercent;
        public String source;

        public StockRecord() {}

        public StockRecord(String symbol, BigDecimal price, Long volume, LocalDateTime timestamp, 
                          BigDecimal change, BigDecimal changePercent, String source) {
            this.symbol = symbol;
            this.price = price;
            this.volume = volume;
            this.timestamp = timestamp;
            this.change = change;
            this.changePercent = changePercent;
            this.source = source;
        }

        @Override
        public String toString() {
            return "StockRecord{" +
                    "symbol='" + symbol + '\'' +
                    ", price=" + price +
                    ", volume=" + volume +
                    ", timestamp=" + timestamp +
                    ", changePercent=" + changePercent +
                    '}';
        }
    }

    public static class StockAlert {
        public String symbol;
        public String alertType;
        public BigDecimal currentPrice;
        public BigDecimal previousPrice;
        public BigDecimal changePercent;
        public Long volume;
        public LocalDateTime timestamp;

        public StockAlert() {}

        public StockAlert(String symbol, String alertType, BigDecimal currentPrice, 
                         BigDecimal previousPrice, BigDecimal changePercent, Long volume) {
            this.symbol = symbol;
            this.alertType = alertType;
            this.currentPrice = currentPrice;
            this.previousPrice = previousPrice;
            this.changePercent = changePercent;
            this.volume = volume;
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * Parse JSON stock data from Kafka
     */
    public static class StockDataParser implements MapFunction<String, StockRecord> {
        @Override
        public StockRecord map(String value) throws Exception {
            try {
                JsonNode json = objectMapper.readTree(value);
                
                StockRecord record = new StockRecord();
                record.symbol = json.get("symbol").asText();
                record.price = new BigDecimal(json.get("price").asText());
                record.volume = json.get("volume").asLong();
                record.source = json.get("source").asText();
                
                // Parse timestamp
                long timestampLong = json.get("timestamp").asLong();
                record.timestamp = LocalDateTime.ofEpochSecond(timestampLong, 0, java.time.ZoneOffset.UTC);
                
                // Parse optional fields
                if (json.has("change") && !json.get("change").isNull()) {
                    record.change = new BigDecimal(json.get("change").asText());
                }
                if (json.has("change_percent") && !json.get("change_percent").isNull()) {
                    record.changePercent = new BigDecimal(json.get("change_percent").asText());
                }
                
                return record;
                
            } catch (Exception e) {
                logger.error("Failed to parse stock data: {}", value, e);
                throw e;
            }
        }
    }

    /**
     * Calculate moving averages for stock prices
     */
    public static class MovingAverageCalculator 
            extends ProcessWindowFunction<StockRecord, Tuple4<String, BigDecimal, BigDecimal, Integer>, String, TimeWindow> {
        
        @Override
        public void process(String symbol, Context context, 
                          Iterable<StockRecord> elements, 
                          Collector<Tuple4<String, BigDecimal, BigDecimal, Integer>> out) {
            
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal minPrice = null;
            BigDecimal maxPrice = null;
            int count = 0;
            
            for (StockRecord record : elements) {
                sum = sum.add(record.price);
                count++;
                
                if (minPrice == null || record.price.compareTo(minPrice) < 0) {
                    minPrice = record.price;
                }
                if (maxPrice == null || record.price.compareTo(maxPrice) > 0) {
                    maxPrice = record.price;
                }
            }
            
            if (count > 0) {
                BigDecimal avgPrice = sum.divide(new BigDecimal(count), 4, BigDecimal.ROUND_HALF_UP);
                out.collect(new Tuple4<>(symbol, avgPrice, maxPrice.subtract(minPrice), count));
            }
        }
    }

    /**
     * Detect significant price changes for alerts
     */
    public static class PriceChangeDetector implements MapFunction<StockRecord, StockAlert> {
        private static final BigDecimal SIGNIFICANT_CHANGE_THRESHOLD = new BigDecimal("5.0"); // 5%
        
        @Override
        public StockAlert map(StockRecord record) throws Exception {
            if (record.changePercent != null && 
                record.changePercent.abs().compareTo(SIGNIFICANT_CHANGE_THRESHOLD) >= 0) {
                
                String alertType = record.changePercent.compareTo(BigDecimal.ZERO) > 0 ? 
                    "SIGNIFICANT_INCREASE" : "SIGNIFICANT_DECREASE";
                
                return new StockAlert(
                    record.symbol,
                    alertType,
                    record.price,
                    record.price.subtract(record.change != null ? record.change : BigDecimal.ZERO),
                    record.changePercent,
                    record.volume
                );
            }
            return null;
        }
    }

    /**
     * Calculate volume anomalies
     */
    public static class VolumeAnomalyDetector 
            extends ProcessWindowFunction<StockRecord, StockAlert, String, TimeWindow> {
        
        @Override
        public void process(String symbol, Context context, 
                          Iterable<StockRecord> elements, 
                          Collector<StockAlert> out) {
            
            long totalVolume = 0;
            int count = 0;
            StockRecord latestRecord = null;
            
            for (StockRecord record : elements) {
                totalVolume += record.volume;
                count++;
                if (latestRecord == null || record.timestamp.isAfter(latestRecord.timestamp)) {
                    latestRecord = record;
                }
            }
            
            if (count > 1 && latestRecord != null) {
                long avgVolume = totalVolume / count;
                
                // Check if current volume is significantly higher than average
                if (latestRecord.volume > avgVolume * 3) { // 3x average volume
                    StockAlert alert = new StockAlert(
                        symbol,
                        "HIGH_VOLUME",
                        latestRecord.price,
                        latestRecord.price,
                        latestRecord.changePercent != null ? latestRecord.changePercent : BigDecimal.ZERO,
                        latestRecord.volume
                    );
                    out.collect(alert);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Setup execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // Checkpoint every minute
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        
        // Configure parallelism
        env.setParallelism(2);

        // Setup Kafka source
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId("flink-stock-processor")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Create data stream from Kafka
        DataStream<String> rawStockStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> System.currentTimeMillis()),
                "Kafka Source"
        );

        // Parse JSON to StockRecord
        DataStream<StockRecord> stockStream = rawStockStream
                .map(new StockDataParser())
                .name("Parse Stock Data");

        // 1. Calculate 5-minute moving averages
        DataStream<Tuple4<String, BigDecimal, BigDecimal, Integer>> movingAverages = stockStream
                .keyBy(record -> record.symbol)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .process(new MovingAverageCalculator())
                .name("Calculate Moving Averages");

        // 2. Detect significant price changes
        DataStream<StockAlert> priceChangeAlerts = stockStream
                .map(new PriceChangeDetector())
                .filter(alert -> alert != null)
                .name("Detect Price Changes");

        // 3. Detect volume anomalies using 1-hour windows
        DataStream<StockAlert> volumeAlerts = stockStream
                .keyBy(record -> record.symbol)
                .window(TumblingProcessingTimeWindows.of(Time.hours(1)))
                .process(new VolumeAnomalyDetector())
                .name("Detect Volume Anomalies");

        // 4. Store processed data to PostgreSQL
        stockStream.addSink(
                JdbcSink.sink(
                        "INSERT INTO stock_data_processed (symbol, price, volume, timestamp, change_percent, avg_price_5min, source) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (symbol, timestamp) DO UPDATE SET " +
                        "price = EXCLUDED.price, volume = EXCLUDED.volume",
                        (PreparedStatement statement, StockRecord record) -> {
                            try {
                                statement.setString(1, record.symbol);
                                statement.setBigDecimal(2, record.price);
                                statement.setLong(3, record.volume);
                                statement.setObject(4, record.timestamp);
                                statement.setBigDecimal(5, record.changePercent);
                                statement.setBigDecimal(6, record.price); // Placeholder for avg price
                                statement.setString(7, record.source);
                            } catch (SQLException e) {
                                logger.error("Error setting prepared statement parameters", e);
                                throw new RuntimeException(e);
                            }
                        },
                        JdbcExecutionOptions.builder()
                                .withBatchSize(100)
                                .withBatchIntervalMs(5000)
                                .withMaxRetries(3)
                                .build(),
                        new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                .withUrl(JDBC_URL)
                                .withDriverName("org.postgresql.Driver")
                                .withUsername(JDBC_USERNAME)
                                .withPassword(JDBC_PASSWORD)
                                .build()
                )
        ).name("Store to PostgreSQL");

        // 5. Send alerts back to Kafka
        priceChangeAlerts.union(volumeAlerts)
                .map(alert -> objectMapper.writeValueAsString(alert))
                .sinkTo(
                        org.apache.flink.connector.kafka.sink.KafkaSink.<String>builder()
                                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                                .setRecordSerializer(
                                        org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.builder()
                                                .setTopic(OUTPUT_TOPIC_ALERTS)
                                                .setValueSerializationSchema(new SimpleStringSchema())
                                                .build()
                                )
                                .build()
                ).name("Send Alerts to Kafka");

        // Print processing results for monitoring
        movingAverages.print("Moving Averages");
        priceChangeAlerts.print("Price Alerts");
        volumeAlerts.print("Volume Alerts");

        // Execute the job
        env.execute("Stock Data Processing Job");
    }
}