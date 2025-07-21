package com.stocktracker.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT Authentication Filter for API Gateway
 * Validates JWT tokens and adds user information to request headers
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Value("${jwt.secret:mySecretKey}")
    private String jwtSecret;

    @Value("${jwt.header:Authorization}")
    private String jwtHeader;

    private final List<String> excludedPaths = List.of(
            "/api/users/register",
            "/api/users/login",
            "/health",
            "/metrics",
            "/actuator"
    );

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // Skip authentication for excluded paths
            if (isExcludedPath(path)) {
                return chain.filter(exchange);
            }

            // Check for Authorization header
            if (!request.getHeaders().containsKey(jwtHeader)) {
                return onError(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(jwtHeader);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid authorization header format", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                // Validate JWT token
                Claims claims = validateToken(token);
                
                // Add user information to request headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", claims.getSubject())
                        .header("X-User-Email", claims.get("email", String.class))
                        .header("X-User-Roles", claims.get("roles", String.class))
                        .build();

                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(modifiedRequest)
                        .build();

                return chain.filter(modifiedExchange);

            } catch (Exception e) {
                return onError(exchange, "Invalid JWT token: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private boolean isExcludedPath(String path) {
        return excludedPaths.stream().anyMatch(path::startsWith);
    }

    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        
        String body = String.format("{\"error\": \"%s\", \"status\": %d}", message, status.value());
        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }

    public static class Config {
        // Configuration properties if needed
    }
}