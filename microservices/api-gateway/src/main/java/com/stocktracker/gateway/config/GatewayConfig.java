package com.stocktracker.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Gateway configuration including CORS, rate limiting, and routing
 */
@Configuration
public class GatewayConfig {

    /**
     * Configure CORS for the gateway
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        final CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(Collections.singletonList("*"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        corsConfig.setAllowCredentials(true);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    /**
     * Configure additional routes with rate limiting
     */
    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Health check route
                .route("health-check", r -> r
                        .path("/health")
                        .filters(f -> f.requestRateLimiter(config -> config
                                .setRateLimiter(redisRateLimiter())
                                .setKeyResolver(hostAddrKeyResolver())
                        ))
                        .uri("http://localhost:8080/actuator/health")
                )
                
                // Metrics route (protected)
                .route("metrics", r -> r
                        .path("/metrics")
                        .filters(f -> f
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(hostAddrKeyResolver())
                                )
                        )
                        .uri("http://localhost:8080/actuator/prometheus")
                )
                
                // Fallback routes
                .route("fallback-user", r -> r
                        .path("/fallback/user-service")
                        .uri("forward:/fallback/generic")
                )
                .route("fallback-data", r -> r
                        .path("/fallback/data-consumer")
                        .uri("forward:/fallback/generic")
                )
                .route("fallback-notification", r -> r
                        .path("/fallback/notification-service")
                        .uri("forward:/fallback/generic")
                )
                
                .build();
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter redisRateLimiter() {
        return new org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter(10, 20, 1);
    }

    @Bean
    public org.springframework.cloud.gateway.filter.ratelimit.KeyResolver hostAddrKeyResolver() {
        return exchange -> reactor.core.publisher.Mono.just(
                exchange.getRequest().getRemoteAddress() != null 
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown"
        );
    }
}