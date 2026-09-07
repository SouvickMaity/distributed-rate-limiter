package com.souvick.rate_limiter.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Component
@Data
@ConfigurationProperties(prefix = "spring.redis")
public class RedisProperties {

    private String host="localhost";

    private int port=6379;

    private int timeout=2000;

    @Bean
    public JedisPool getJedisPool(){
        JedisPoolConfig poolConfig = new JedisPoolConfig();
         poolConfig.setMaxTotal(300);      // was 50
    poolConfig.setMaxIdle(100);       // was 10
    poolConfig.setMinIdle(20);        // was 5
    poolConfig.setTestOnBorrow(false); // was true 
    poolConfig.setTestOnReturn(false); // was true
    poolConfig.setBlockWhenExhausted(true);
    poolConfig.setMaxWaitMillis(1000);
    return new JedisPool(poolConfig, host, port, timeout);
    }
}


