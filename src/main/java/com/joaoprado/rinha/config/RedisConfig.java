package com.joaoprado.rinha.config;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisConfig {

    private ClientResources clientResources;
    private RedisClient redisClient;

    @Bean
    public ClientResources clientResources() {
        this.clientResources = DefaultClientResources.builder()
            .ioThreadPoolSize(2)
            .computationThreadPoolSize(2)
            .build();
        return this.clientResources;
    }

    @Bean
    public RedisClient redisClient(ClientResources clientResources) {
        RedisURI redisUri = RedisURI.builder()
            .withHost("redis")
            .withPort(6379)
            .withTimeout(Duration.ofMillis(500))
            .build();

        this.redisClient = RedisClient.create(clientResources, redisUri);

        this.redisClient.setOptions(ClientOptions.builder()
            .socketOptions(SocketOptions.builder()
                .keepAlive(true)
                .tcpNoDelay(true)
                .build())
            .timeoutOptions(TimeoutOptions.builder()
                .fixedTimeout(Duration.ofMillis(1000))
                .build())
            .build());

        return this.redisClient;
    }

    @Bean
    public StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
        var connection = redisClient.connect();
        try {
            connection.sync().ping();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Redis", e);
        }
        return connection;
    }

    @PreDestroy
    public void cleanup() {
        if (redisClient != null) redisClient.shutdown();
        if (clientResources != null) clientResources.shutdown();
    }
}
