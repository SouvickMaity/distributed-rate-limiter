package com.souvick.rate_limiter.service;

import com.souvick.rate_limiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties properties;

    private static final String TOKENS_KEY_PREFIX =
            "rate_limiter:tokens:";

    private static final String LAST_REFILL_KEY_PREFIX =
            "rate_limiter:last_refill:";

    /*
     * Atomic Token Bucket:
     *
     * 1. Get current token count
     * 2. Get last refill timestamp
     * 3. Calculate elapsed time
     * 4. Refill tokens according to refill rate
     * 5. Cap tokens at capacity
     * 6. Consume one token if available
     *
     * All operations execute atomically inside Redis.
     */
    
    private static final String TOKEN_BUCKET_SCRIPT = """

        local tokensKey = KEYS[1]
        local lastRefillKey = KEYS[2]

        local capacity = tonumber(ARGV[1])
        local refillRate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        local tokens = redis.call('GET', tokensKey)
        local lastRefill = redis.call('GET', lastRefillKey)

        -- First request for this client
        if tokens == false or lastRefill == false then

            tokens = capacity - 1

            redis.call('SET', tokensKey, tokens)
            redis.call('SET', lastRefillKey, now)

            return tokens

        end

        tokens = tonumber(tokens)
        lastRefill = tonumber(lastRefill)

        -- Calculate elapsed milliseconds
        local elapsed = now - lastRefill

        if elapsed > 0 then

            -- Calculate how many tokens should be added
            local tokensToAdd = math.floor(
                (elapsed * refillRate) / 1000
            )

            if tokensToAdd > 0 then

                tokens = math.min(
                    capacity,
                    tokens + tokensToAdd
                )

                -- Preserve unused fractional time
                local consumedTime =
                    math.floor((tokensToAdd * 1000) / refillRate)

                lastRefill = lastRefill + consumedTime

                redis.call('SET', tokensKey, tokens)
                redis.call('SET', lastRefillKey, lastRefill)

            end

        end

        -- No token available
        if tokens <= 0 then
            return -1
        end

        -- Consume one token
        tokens = tokens - 1

        redis.call('SET', tokensKey, tokens)

        return tokens
        """;

    /**
     * Checks whether a request is allowed.
     *
     * @return true if a token was available and consumed
     *         false if the bucket was empty
     */
    public boolean isAllowed(String clientId) {

        String tokensKey =
                TOKENS_KEY_PREFIX + clientId;

        String lastRefillKey =
                LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();

        try (Jedis jedis = jedisPool.getResource()) {

            Object result = jedis.eval(
                    TOKEN_BUCKET_SCRIPT,
                    2,
                    tokensKey,
                    lastRefillKey,
                    String.valueOf(properties.getCapacity()),
                    String.valueOf(properties.getRefillRate()),
                    String.valueOf(now)
            );

            long remainingTokens =
                    Long.parseLong(result.toString());

            return remainingTokens >= 0;
        }
    }

    /**
     * Returns configured bucket capacity.
     */
    public long getCapacity(String clientId) {
        return properties.getCapacity();
    }

    /**
     * Returns the current available tokens.
     *
     * This method performs refill calculation atomically
     * but does not consume a token.
     */
    public long getAvailableTokens(String clientId) {

        String tokensKey =
                TOKENS_KEY_PREFIX + clientId;

        String lastRefillKey =
                LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();

        String getTokensScript = """

            local tokensKey = KEYS[1]
            local lastRefillKey = KEYS[2]

            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local tokens = redis.call('GET', tokensKey)
            local lastRefill = redis.call('GET', lastRefillKey)

            -- Bucket has not been initialized
            if tokens == false or lastRefill == false then
                return capacity
            end

            tokens = tonumber(tokens)
            lastRefill = tonumber(lastRefill)

            local elapsed = now - lastRefill

            if elapsed > 0 then

                local tokensToAdd = math.floor(
                    (elapsed * refillRate) / 1000
                )

                if tokensToAdd > 0 then

                    tokens = math.min(
                        capacity,
                        tokens + tokensToAdd
                    )

                    local consumedTime =
                        math.floor(
                            (tokensToAdd * 1000) / refillRate
                        )

                    lastRefill =
                        lastRefill + consumedTime

                    redis.call('SET', tokensKey, tokens)
                    redis.call('SET', lastRefillKey, lastRefill)

                end
            end

            return tokens
            """;

        try (Jedis jedis = jedisPool.getResource()) {

            Object result = jedis.eval(
                    getTokensScript,
                    2,
                    tokensKey,
                    lastRefillKey,
                    String.valueOf(properties.getCapacity()),
                    String.valueOf(properties.getRefillRate()),
                    String.valueOf(now)
            );

            return Long.parseLong(result.toString());
        }
    }
}

