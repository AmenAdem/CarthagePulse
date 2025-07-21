package com.stocktracker.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Flink job for processing news data streams
 * Performs sentiment analysis, keyword extraction, and generates trading signals
 */
public class NewsDataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NewsDataProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String INPUT_TOPIC = "news-data";
    private static final String OUTPUT_TOPIC_SIGNALS = "trading-signals";
    private static final String OUTPUT_TOPIC_ALERTS = "news-alerts";

    // Market-related keywords for filtering
    private static final List<String> MARKET_KEYWORDS = Arrays.asList(
            "earnings", "profit", "revenue", "loss", "merger", "acquisition",
            "ipo", "dividend", "buyback", "partnership", "contract", "lawsuit",
            "fda", "approval", "recall", "bankruptcy", "restructuring", "ceo",
            "guidance", "forecast", "upgrade", "downgrade", "bullish", "bearish"
    );

    public static class NewsRecord {
        public String title;
        public String content;
        public String url;
        public LocalDateTime timestamp;
        public String source;
        public Double sentimentScore;
        public List<String> symbols;
        public String category;

        public NewsRecord() {}

        public NewsRecord(String title, String content, String url, LocalDateTime timestamp,
                         String source, Double sentimentScore, List<String> symbols, String category) {
            this.title = title;
            this.content = content;
            this.url = url;
            this.timestamp = timestamp;
            this.source = source;
            this.sentimentScore = sentimentScore;
            this.symbols = symbols;
            this.category = category;
        }

        @Override
        public String toString() {
            return "NewsRecord{" +
                    "title='" + title + '\'' +
                    ", timestamp=" + timestamp +
                    ", sentimentScore=" + sentimentScore +
                    ", symbols=" + symbols +
                    '}';
        }
    }

    public static class TradingSignal {
        public String symbol;
        public String signalType; // BUY, SELL, HOLD
        public Double confidence;
        public String reason;
        public LocalDateTime timestamp;
        public String source;

        public TradingSignal() {}

        public TradingSignal(String symbol, String signalType, Double confidence, 
                           String reason, String source) {
            this.symbol = symbol;
            this.signalType = signalType;
            this.confidence = confidence;
            this.reason = reason;
            this.source = source;
            this.timestamp = LocalDateTime.now();
        }
    }

    public static class NewsAlert {
        public String headline;
        public String summary;
        public String url;
        public List<String> affectedSymbols;
        public Double sentimentScore;
        public String alertType;
        public LocalDateTime timestamp;

        public NewsAlert() {}

        public NewsAlert(String headline, String summary, String url, 
                        List<String> affectedSymbols, Double sentimentScore, String alertType) {
            this.headline = headline;
            this.summary = summary;
            this.url = url;
            this.affectedSymbols = affectedSymbols;
            this.sentimentScore = sentimentScore;
            this.alertType = alertType;
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * Parse JSON news data from Kafka
     */
    public static class NewsDataParser implements MapFunction<String, NewsRecord> {
        @Override
        public NewsRecord map(String value) throws Exception {
            try {
                JsonNode json = objectMapper.readTree(value);
                
                NewsRecord record = new NewsRecord();
                record.title = json.get("title").asText();
                record.content = json.get("content").asText();
                record.url = json.get("url").asText();
                record.source = json.get("source").asText();
                record.category = json.get("category").asText();
                
                // Parse timestamp
                long timestampLong = json.get("timestamp").asLong();
                record.timestamp = LocalDateTime.ofEpochSecond(timestampLong, 0, java.time.ZoneOffset.UTC);
                
                // Parse sentiment score
                if (json.has("sentiment_score") && !json.get("sentiment_score").isNull()) {
                    record.sentimentScore = json.get("sentiment_score").asDouble();
                }
                
                // Parse symbols array
                if (json.has("symbols") && json.get("symbols").isArray()) {
                    record.symbols = objectMapper.convertValue(
                        json.get("symbols"), 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                    );
                }
                
                return record;
                
            } catch (Exception e) {
                logger.error("Failed to parse news data: {}", value, e);
                throw e;
            }
        }
    }

    /**
     * Calculate simple sentiment score if not provided
     */
    public static class SentimentAnalyzer implements MapFunction<NewsRecord, NewsRecord> {
        private static final List<String> POSITIVE_WORDS = Arrays.asList(
                "growth", "profit", "increase", "gain", "success", "positive", 
                "bullish", "buy", "upgrade", "strong", "beat", "exceed"
        );
        
        private static final List<String> NEGATIVE_WORDS = Arrays.asList(
                "loss", "decline", "decrease", "fall", "negative", "bearish", 
                "sell", "downgrade", "weak", "miss", "below", "concern"
        );
        
        @Override
        public NewsRecord map(NewsRecord record) throws Exception {
            if (record.sentimentScore == null) {
                record.sentimentScore = calculateSentiment(record.title + " " + record.content);
            }
            return record;
        }
        
        private double calculateSentiment(String text) {
            String lowerText = text.toLowerCase();
            int positiveCount = 0;
            int negativeCount = 0;
            
            for (String word : POSITIVE_WORDS) {
                if (lowerText.contains(word)) {
                    positiveCount++;
                }
            }
            
            for (String word : NEGATIVE_WORDS) {
                if (lowerText.contains(word)) {
                    negativeCount++;
                }
            }
            
            if (positiveCount + negativeCount == 0) {
                return 0.0; // Neutral
            }
            
            return (double) (positiveCount - negativeCount) / (positiveCount + negativeCount);
        }
    }

    /**
     * Extract symbols from news content if not provided
     */
    public static class SymbolExtractor implements MapFunction<NewsRecord, NewsRecord> {
        // Common stock symbols pattern (simplified)
        private static final List<String> KNOWN_SYMBOLS = Arrays.asList(
                "AAPL", "GOOGL", "MSFT", "TSLA", "AMZN", "META", "NVDA", 
                "JPM", "V", "WMT", "DIS", "NFLX", "AMD", "INTC", "CSCO"
        );
        
        @Override
        public NewsRecord map(NewsRecord record) throws Exception {
            if (record.symbols == null || record.symbols.isEmpty()) {
                record.symbols = extractSymbolsFromText(record.title + " " + record.content);
            }
            return record;
        }
        
        private List<String> extractSymbolsFromText(String text) {
            return KNOWN_SYMBOLS.stream()
                    .filter(symbol -> text.toUpperCase().contains(symbol))
                    .toList();
        }
    }

    /**
     * Generate trading signals based on news sentiment
     */
    public static class TradingSignalGenerator implements MapFunction<NewsRecord, TradingSignal> {
        @Override
        public TradingSignal map(NewsRecord record) throws Exception {
            if (record.symbols != null && !record.symbols.isEmpty() && record.sentimentScore != null) {
                for (String symbol : record.symbols) {
                    if (Math.abs(record.sentimentScore) > 0.3) { // Significant sentiment
                        String signalType;
                        double confidence = Math.abs(record.sentimentScore);
                        
                        if (record.sentimentScore > 0.3) {
                            signalType = "BUY";
                        } else if (record.sentimentScore < -0.3) {
                            signalType = "SELL";
                        } else {
                            signalType = "HOLD";
                        }
                        
                        return new TradingSignal(
                                symbol,
                                signalType,
                                confidence,
                                "News sentiment: " + record.sentimentScore + " - " + record.title,
                                "news-analysis"
                        );
                    }
                }
            }
            return null;
        }
    }

    /**
     * Filter and create news alerts for significant events
     */
    public static class NewsAlertGenerator implements MapFunction<NewsRecord, NewsAlert> {
        @Override
        public NewsAlert map(NewsRecord record) throws Exception {
            // Check if news contains market-relevant keywords
            String fullText = (record.title + " " + record.content).toLowerCase();
            boolean isMarketRelevant = MARKET_KEYWORDS.stream()
                    .anyMatch(fullText::contains);
            
            if (isMarketRelevant && record.symbols != null && !record.symbols.isEmpty()) {
                String alertType = "MARKET_NEWS";
                
                // Determine alert type based on sentiment
                if (record.sentimentScore != null) {
                    if (record.sentimentScore > 0.5) {
                        alertType = "POSITIVE_NEWS";
                    } else if (record.sentimentScore < -0.5) {
                        alertType = "NEGATIVE_NEWS";
                    }
                }
                
                return new NewsAlert(
                        record.title,
                        record.content.length() > 200 ? 
                            record.content.substring(0, 200) + "..." : record.content,
                        record.url,
                        record.symbols,
                        record.sentimentScore,
                        alertType
                );
            }
            
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        // Setup execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Enable checkpointing
        env.enableCheckpointing(60000);
        env.setParallelism(2);

        // Setup Kafka source
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId("flink-news-processor")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Create data stream from Kafka
        DataStream<String> rawNewsStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                        .withTimestampAssigner((event, timestamp) -> System.currentTimeMillis()),
                "Kafka News Source"
        );

        // Parse and process news data
        DataStream<NewsRecord> newsStream = rawNewsStream
                .map(new NewsDataParser())
                .map(new SentimentAnalyzer())
                .map(new SymbolExtractor())
                .name("Process News Data");

        // Generate trading signals
        DataStream<TradingSignal> tradingSignals = newsStream
                .map(new TradingSignalGenerator())
                .filter(signal -> signal != null)
                .name("Generate Trading Signals");

        // Generate news alerts
        DataStream<NewsAlert> newsAlerts = newsStream
                .map(new NewsAlertGenerator())
                .filter(alert -> alert != null)
                .name("Generate News Alerts");

        // Use Complex Event Processing (CEP) to detect patterns
        Pattern<NewsRecord, ?> breakingNewsPattern = Pattern.<NewsRecord>begin("start")
                .where(new SimpleCondition<NewsRecord>() {
                    @Override
                    public boolean filter(NewsRecord record) {
                        return record.sentimentScore != null && Math.abs(record.sentimentScore) > 0.7;
                    }
                })
                .followedBy("followUp")
                .where(new SimpleCondition<NewsRecord>() {
                    @Override
                    public boolean filter(NewsRecord record) {
                        return record.sentimentScore != null && Math.abs(record.sentimentScore) > 0.5;
                    }
                })
                .within(Time.minutes(30));

        PatternStream<NewsRecord> patternStream = CEP.pattern(
                newsStream.keyBy(record -> record.symbols != null && !record.symbols.isEmpty() ? 
                    record.symbols.get(0) : "UNKNOWN"),
                breakingNewsPattern
        );

        DataStream<NewsAlert> breakingNewsAlerts = patternStream.select(
                new PatternSelectFunction<NewsRecord, NewsAlert>() {
                    @Override
                    public NewsAlert select(Map<String, List<NewsRecord>> pattern) {
                        List<NewsRecord> startEvents = pattern.get("start");
                        List<NewsRecord> followUpEvents = pattern.get("followUp");
                        
                        if (!startEvents.isEmpty() && !followUpEvents.isEmpty()) {
                            NewsRecord firstEvent = startEvents.get(0);
                            return new NewsAlert(
                                    "Breaking News Pattern Detected: " + firstEvent.title,
                                    "Multiple significant news events detected in short timeframe",
                                    firstEvent.url,
                                    firstEvent.symbols,
                                    firstEvent.sentimentScore,
                                    "BREAKING_NEWS_PATTERN"
                            );
                        }
                        return null;
                    }
                }
        );

        // Aggregate sentiment by symbol over time windows
        DataStream<String> sentimentAggregates = newsStream
                .filter(record -> record.symbols != null && !record.symbols.isEmpty())
                .flatMap((NewsRecord record, org.apache.flink.util.Collector<org.apache.flink.api.java.tuple.Tuple2<String, Double>> out) -> {
                    if (record.sentimentScore != null) {
                        for (String symbol : record.symbols) {
                            out.collect(new org.apache.flink.api.java.tuple.Tuple2<>(symbol, record.sentimentScore));
                        }
                    }
                })
                .keyBy(tuple -> tuple.f0)
                .window(TumblingProcessingTimeWindows.of(Time.hours(1)))
                .reduce((tuple1, tuple2) -> new org.apache.flink.api.java.tuple.Tuple2<>(tuple1.f0, (tuple1.f1 + tuple2.f1) / 2))
                .map(tuple -> String.format("Symbol: %s, Avg Sentiment: %.3f", tuple.f0, tuple.f1))
                .name("Aggregate Sentiment");

        // Send trading signals to Kafka
        tradingSignals
                .map(signal -> objectMapper.writeValueAsString(signal))
                .sinkTo(
                        org.apache.flink.connector.kafka.sink.KafkaSink.<String>builder()
                                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                                .setRecordSerializer(
                                        org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.builder()
                                                .setTopic(OUTPUT_TOPIC_SIGNALS)
                                                .setValueSerializationSchema(new SimpleStringSchema())
                                                .build()
                                )
                                .build()
                ).name("Send Trading Signals");

        // Send news alerts to Kafka
        newsAlerts.union(breakingNewsAlerts)
                .filter(alert -> alert != null)
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
                ).name("Send News Alerts");

        // Print results for monitoring
        tradingSignals.print("Trading Signals");
        newsAlerts.print("News Alerts");
        sentimentAggregates.print("Sentiment Aggregates");

        // Execute the job
        env.execute("News Data Processing Job");
    }
}