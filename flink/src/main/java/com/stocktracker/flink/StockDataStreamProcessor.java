package com.stocktracker.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;

public class StockDataStreamProcessor {
    
    private static final Logger LOG = LoggerFactory.getLogger(StockDataStreamProcessor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Configure Kafka properties
        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        
        // Create Kafka source for stock prices
        KafkaSource<String> stockPriceSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics("stock-prices")
                .setGroupId("flink-processor")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Create Kafka source for news articles
        KafkaSource<String> newsSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics("news-articles")
                .setGroupId("flink-processor")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Create Kafka sink for processed alerts
        KafkaSink<String> alertSink = KafkaSink.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("stock-alerts")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        // Process stock price stream
        DataStream<String> stockPriceStream = env
                .fromSource(stockPriceSource, WatermarkStrategy.noWatermarks(), "Stock Price Source");

        DataStream<StockPriceEvent> parsedStockPrices = stockPriceStream
                .map(new StockPriceParser())
                .name("Parse Stock Prices");

        // Detect price alerts (significant price changes)
        DataStream<String> priceAlerts = parsedStockPrices
                .keyBy(StockPriceEvent::getCompanyId)
                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
                .process(new PriceChangeDetector())
                .name("Price Change Detection");

        // Process news stream
        DataStream<String> newsStream = env
                .fromSource(newsSource, WatermarkStrategy.noWatermarks(), "News Source");

        DataStream<NewsEvent> parsedNews = newsStream
                .map(new NewsParser())
                .name("Parse News");

        // Detect sentiment-based alerts
        DataStream<String> sentimentAlerts = parsedNews
                .filter(news -> Math.abs(news.getSentimentScore()) > 0.5)
                .map(new SentimentAlertGenerator())
                .name("Sentiment Alert Generation");

        // Combine all alerts
        DataStream<String> allAlerts = priceAlerts.union(sentimentAlerts);

        // Send alerts to Kafka
        allAlerts.sinkTo(alertSink).name("Alert Sink");

        // Print alerts for debugging
        allAlerts.print("Alerts");

        // Execute the program
        env.execute("Stock Data Stream Processor");
    }

    // Stock Price Event POJO
    public static class StockPriceEvent {
        private int companyId;
        private String symbol;
        private double price;
        private long volume;
        private double changePercent;
        private String timestamp;

        // Constructors
        public StockPriceEvent() {}

        public StockPriceEvent(int companyId, String symbol, double price, long volume, double changePercent, String timestamp) {
            this.companyId = companyId;
            this.symbol = symbol;
            this.price = price;
            this.volume = volume;
            this.changePercent = changePercent;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public int getCompanyId() { return companyId; }
        public void setCompanyId(int companyId) { this.companyId = companyId; }
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        
        public long getVolume() { return volume; }
        public void setVolume(long volume) { this.volume = volume; }
        
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // News Event POJO
    public static class NewsEvent {
        private int companyId;
        private String symbol;
        private String title;
        private String content;
        private double sentimentScore;
        private String timestamp;

        // Constructors
        public NewsEvent() {}

        public NewsEvent(int companyId, String symbol, String title, String content, double sentimentScore, String timestamp) {
            this.companyId = companyId;
            this.symbol = symbol;
            this.title = title;
            this.content = content;
            this.sentimentScore = sentimentScore;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public int getCompanyId() { return companyId; }
        public void setCompanyId(int companyId) { this.companyId = companyId; }
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public double getSentimentScore() { return sentimentScore; }
        public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    // Stock Price Parser
    public static class StockPriceParser implements MapFunction<String, StockPriceEvent> {
        @Override
        public StockPriceEvent map(String value) throws Exception {
            try {
                JsonNode json = objectMapper.readTree(value);
                return new StockPriceEvent(
                    json.get("companyId").asInt(),
                    json.get("symbol").asText(),
                    json.get("price").asDouble(),
                    json.get("volume").asLong(),
                    json.get("changePercent").asDouble(),
                    json.get("timestamp").asText()
                );
            } catch (Exception e) {
                LOG.error("Error parsing stock price: " + value, e);
                throw e;
            }
        }
    }

    // News Parser
    public static class NewsParser implements MapFunction<String, NewsEvent> {
        @Override
        public NewsEvent map(String value) throws Exception {
            try {
                JsonNode json = objectMapper.readTree(value);
                return new NewsEvent(
                    json.get("companyId").asInt(),
                    json.get("symbol").asText(),
                    json.get("title").asText(),
                    json.get("content").asText(),
                    json.get("sentimentScore").asDouble(),
                    json.get("timestamp").asText()
                );
            } catch (Exception e) {
                LOG.error("Error parsing news: " + value, e);
                throw e;
            }
        }
    }

    // Price Change Detector
    public static class PriceChangeDetector extends ProcessWindowFunction<StockPriceEvent, String, Integer, TimeWindow> {
        @Override
        public void process(Integer companyId, Context context, Iterable<StockPriceEvent> elements, Collector<String> out) throws Exception {
            double maxPrice = Double.MIN_VALUE;
            double minPrice = Double.MAX_VALUE;
            StockPriceEvent latestEvent = null;

            for (StockPriceEvent event : elements) {
                maxPrice = Math.max(maxPrice, event.getPrice());
                minPrice = Math.min(minPrice, event.getPrice());
                latestEvent = event;
            }

            if (latestEvent != null && maxPrice != minPrice) {
                double priceChangePercent = ((maxPrice - minPrice) / minPrice) * 100;
                
                if (priceChangePercent > 5.0) { // Alert if price changed more than 5%
                    String alert = String.format(
                        "{\"type\":\"price_alert\",\"companyId\":%d,\"symbol\":\"%s\",\"priceChange\":%.2f,\"currentPrice\":%.2f,\"timestamp\":\"%s\"}",
                        companyId, latestEvent.getSymbol(), priceChangePercent, latestEvent.getPrice(), latestEvent.getTimestamp()
                    );
                    out.collect(alert);
                    LOG.info("Price alert generated for {}: {}% change", latestEvent.getSymbol(), priceChangePercent);
                }
            }
        }
    }

    // Sentiment Alert Generator
    public static class SentimentAlertGenerator implements MapFunction<NewsEvent, String> {
        @Override
        public String map(NewsEvent news) throws Exception {
            String sentiment = news.getSentimentScore() > 0 ? "positive" : "negative";
            String alert = String.format(
                "{\"type\":\"news_alert\",\"companyId\":%d,\"symbol\":\"%s\",\"sentiment\":\"%s\",\"sentimentScore\":%.2f,\"title\":\"%s\",\"timestamp\":\"%s\"}",
                news.getCompanyId(), news.getSymbol(), sentiment, news.getSentimentScore(), 
                news.getTitle().replace("\"", "\\\""), news.getTimestamp()
            );
            LOG.info("Sentiment alert generated for {}: {} sentiment", news.getSymbol(), sentiment);
            return alert;
        }
    }
}