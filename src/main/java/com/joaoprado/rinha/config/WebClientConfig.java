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
            .maxConnections(500)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(2))
            .pendingAcquireTimeout(Duration.ofSeconds(2))
            .pendingAcquireMaxCount(1000)
            .evictInBackground(Duration.ofSeconds(30))
            .build();
    }

    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        return HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .responseTimeout(Duration.ofSeconds(3))
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
                configurer.defaultCodecs().maxInMemorySize(256 * 1024);
                configurer.defaultCodecs().enableLoggingRequestDetails(false);
            })
            .build();
    }

    @Bean("fallbackProcessorWebClient")
    public WebClient fallbackProcessorWebClient(HttpClient httpClient) {
        return WebClient.builder()
            .baseUrl("http://payment-processor-fallback:8080")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(256 * 1024);
                configurer.defaultCodecs().enableLoggingRequestDetails(false);
            })
            .build();
    }
}