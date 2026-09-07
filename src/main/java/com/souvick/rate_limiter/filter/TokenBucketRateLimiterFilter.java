package com.souvick.rate_limiter.filter;

import com.souvick.rate_limiter.service.RateLimiterService;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class TokenBucketRateLimiterFilter
        extends AbstractGatewayFilterFactory<TokenBucketRateLimiterFilter.Config> {

    private final RateLimiterService rateLimiterService;

    public TokenBucketRateLimiterFilter(
            RateLimiterService rateLimiterService) {

        super(Config.class);
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public Config newConfig() {
        return new Config();
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String clientId = getClientId(request);

            // Check Token Bucket
            if (!rateLimiterService.isAllowed(clientId)) {

                response.setStatusCode(
                        HttpStatus.TOO_MANY_REQUESTS
                );

                addRateLimitHeaders(response, clientId);

                String errorBody = String.format(
                        "{\"error\":\"Rate limit exceeded\",\"clientId\":\"%s\"}",
                        clientId
                );

                return response.writeWith(
                        Mono.just(
                                response.bufferFactory()
                                        .wrap(errorBody.getBytes(
                                                StandardCharsets.UTF_8
                                        ))
                        )
                );
            }

            // Request is allowed
            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() ->
                            addRateLimitHeaders(response, clientId)
                    ));
        };
    }

    private void addRateLimitHeaders(
            ServerHttpResponse response,
            String clientId) {

        response.getHeaders().set(
                "X-RateLimit-Limit",
                String.valueOf(
                        rateLimiterService.getCapacity(clientId)
                )
        );

        response.getHeaders().set(
                "X-RateLimit-Remaining",
                String.valueOf(
                        rateLimiterService.getAvailableTokens(clientId)
                )
        );
    }

    public static class Config {
    }

    private String getClientId(ServerHttpRequest request) {

        // Check X-Forwarded-For first
        String xForwardedFor =
                request.getHeaders()
                        .getFirst("X-Forwarded-For");

        if (xForwardedFor != null &&
                !xForwardedFor.isEmpty()) {

            return xForwardedFor
                    .split(",")[0]
                    .trim();
        }

        // Fallback to direct connection IP
        var remoteAddress =
                request.getRemoteAddress();

        if (remoteAddress != null &&
                remoteAddress.getAddress() != null) {

            return remoteAddress
                    .getAddress()
                    .getHostAddress();
        }

        return "unknown";
    }
}

