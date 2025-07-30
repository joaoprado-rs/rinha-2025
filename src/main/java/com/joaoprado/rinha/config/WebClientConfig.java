package com.joaoprado.rinha.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("payment-processor-pool")
            .maxConnections(100) // Pool maior para alta carga
            .maxIdleTime(Duration.ofSeconds(30)) // Keep connections alive
            .maxLifeTime(Duration.ofMinutes(5)) // Recycle connections
            .pendingAcquireTimeout(Duration.ofMillis(500)) // Timeout para pegar conexão
            .evictInBackground(Duration.ofSeconds(60)) // Cleanup background
            .build();
    }

    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000) // 1s connect timeout
            .option(ChannelOption.SO_KEEPALIVE, true) // TCP keepalive
            .option(ChannelOption.TCP_NODELAY, true) // Disable Nagle algorithm
            .responseTimeout(Duration.ofSeconds(3)) // 3s response timeout
            .doOnConnected(conn -> {
                conn.addHandlerLast(new ReadTimeoutHandler(2, TimeUnit.SECONDS));
                conn.addHandlerLast(new WriteTimeoutHandler(1, TimeUnit.SECONDS));
            });
    }

    @Bean("defaultProcessorWebClient")
    public WebClient defaultProcessorWebClient(HttpClient httpClient) {
        return WebClient.builder()
            .baseUrl("http://payment-processor-default:8080")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(256 * 1024); // 256KB (menor)
                configurer.defaultCodecs().enableLoggingRequestDetails(false); // No logging
            })
            .build();
    }

    @Bean("fallbackProcessorWebClient")
    public WebClient fallbackProcessorWebClient(HttpClient httpClient) {
        return WebClient.builder()
            .baseUrl("http://payment-processor-fallback:8080")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(256 * 1024); // 256KB (menor)
                configurer.defaultCodecs().enableLoggingRequestDetails(false); // No logging
            })
            .build();
    }
}