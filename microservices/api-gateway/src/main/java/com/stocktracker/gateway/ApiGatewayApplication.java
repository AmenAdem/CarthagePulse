package com.stocktracker.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * API Gateway Application - Entry point for all microservices
 * Provides routing, authentication, rate limiting, and monitoring
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Configure routes for microservices
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User Service Routes
                .route("user-service", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .circuitBreaker(config -> config
                                        .setName("user-service-cb")
                                        .setFallbackUri("forward:/fallback/user-service")
                                )
                        )
                        .uri("http://user-service:8081")
                )
                
                // Data Consumer Service Routes
                .route("data-consumer-service", r -> r
                        .path("/api/stocks/**", "/api/data/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .circuitBreaker(config -> config
                                        .setName("data-consumer-cb")
                                        .setFallbackUri("forward:/fallback/data-consumer")
                                )
                        )
                        .uri("http://data-consumer-service:8082")
                )
                
                // Notification Service Routes
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .circuitBreaker(config -> config
                                        .setName("notification-service-cb")
                                        .setFallbackUri("forward:/fallback/notification-service")
                                )
                        )
                        .uri("http://notification-service:8083")
                )
                
                // WebSocket Routes for real-time updates
                .route("websocket-route", r -> r
                        .path("/ws/**")
                        .uri("http://data-consumer-service:8082")
                )
                
                .build();
    }
}