package com.joaoprado.rinha.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    StatefulRedisConnection redisConnection() {
        RedisClient client = RedisClient.create("redis://redis:6379");
        StatefulRedisConnection<String, String> connection = client.connect();
        connection.sync().ping();
        return connection;
    }
}
