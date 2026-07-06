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

    private final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    //Pattern
    //rate_limiter:{type}:{clientId}

    //rate_limiter:tokens:192.168.1.100 > Current Token Count
    //rate_limiter:last_refill:192.168.1.100 > Last Refill Timestamp

    //rate_limiter:tokens:192.168.1.100 > "7"
    // rate_limiter:last_refill:192.168.1.100 > "12324342342"

    public boolean isAllowed(String clientId) {

        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);

            String tokenStr = jedis.get(tokenKey);

            long currentTokens = tokenStr != null ? Long.parseLong(tokenStr) : properties.getCapacity();

            if(currentTokens <= 0) {
                return false;
            }

            long decremented = jedis.decr(tokenKey);
            return decremented >= 0;
        }
    }

    public long getCapacity(String clientId) {

        return properties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {

        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        try(Jedis jedis = jedisPool.getResource()) {

            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return tokenStr != null ? Long.parseLong(tokenStr) : properties.getCapacity();

        }
    }

    public void refillTokens(String clientId, Jedis jedis) {

        String tokensKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);
        if(lastRefillStr == null){

            jedis.set(tokensKey, String.valueOf(properties.getCapacity()));
            jedis.set(lastRefillKey, String.valueOf(now));
            return;
        }

        long lastRefillTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefillTime;

        if(elapsedTime <= 0) {
            return;
        }

        long tokensToAdd = (elapsedTime * properties.getRefillRate()) / 1000;
        if(tokensToAdd <= 0) {
            return;
        }

        String tokenStr = jedis.get(tokensKey);

        long currentTokens = tokenStr !=null ? Long.parseLong(tokenStr) : properties.getCapacity();
        long newTokens = Math.min(properties.getCapacity(), currentTokens + tokensToAdd);

        jedis.set(tokensKey, String.valueOf(newTokens));
        jedis.set(lastRefillKey, String.valueOf(now));

    }

    // tokensToAdd = (elapsedTime * properties.getRefillRate()) / 1000;

    //elapsedTime is in msec
    //refillRate is tokens per second
    //Convert msec to sec  / 1000

    //ElapsedTime > 2000ms (2sec)
    //RefillRate = 5 tokens per second
    //Cal > (2000 * 5) / 1000 > 10 tokens
}
