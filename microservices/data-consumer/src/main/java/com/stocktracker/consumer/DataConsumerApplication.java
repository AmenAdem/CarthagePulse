package com.stocktracker.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Data Consumer Service Application
 * Consumes data from Kafka topics and stores in database
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
public class DataConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataConsumerApplication.class, args);
    }
}