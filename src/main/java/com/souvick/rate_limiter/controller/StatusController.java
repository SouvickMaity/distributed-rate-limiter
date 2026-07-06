package com.souvick.rate_limiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.souvick.rate_limiter.service.RateLimiterService;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for health check and rate limit status endpoints.
 * These endpoints are not routed through the gateway filter.
 * Note: Spring Cloud Gateway is reactive, so this uses reactive types.
 */
@RestController
@RequestMapping("/gateway")
public class StatusController {

    private final RateLimiterService rateLimiterService;

    public StatusController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Rate Limiter Gateway"
        )));
    }

    /**
     * Endpoint to check rate limit status for a client.
     */
    @GetMapping("/rate-limit/status")
    public Mono<ResponseEntity<Map<String, Object>>> getRateLimitStatus(ServerWebExchange exchange) {
        String clientId = getClientId(exchange);
        return Mono.just(ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "capacity", rateLimiterService.getCapacity(clientId),
                "availableTokens", rateLimiterService.getAvailableTokens(clientId)
        )));
    }

    /**
     * Extracts client identifier from the request (IP address).
     */
    private String getClientId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }
}