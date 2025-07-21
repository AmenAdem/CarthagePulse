package com.investorpipeline.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User Service Routes
                .route("user-service", r -> r.path("/api/users/**")
                        .filters(f -> f.circuitBreaker(config -> config
                                .setName("user-service")
                                .setFallbackUri("forward:/fallback/users")))
                        .uri("lb://user-service"))
                
                // Data Consumer Service Routes
                .route("data-consumer", r -> r.path("/api/data/**")
                        .filters(f -> f.circuitBreaker(config -> config
                                .setName("data-consumer")
                                .setFallbackUri("forward:/fallback/data")))
                        .uri("lb://data-consumer-service"))
                
                // Notification Service Routes
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .filters(f -> f.circuitBreaker(config -> config
                                .setName("notification-service")
                                .setFallbackUri("forward:/fallback/notifications")))
                        .uri("lb://notification-service"))
                
                // WebSocket Routes
                .route("websocket", r -> r.path("/ws/**")
                        .uri("lb://data-consumer-service"))
                
                .build();
    }
}