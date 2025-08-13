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
            .maxConnections(200)
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
            .responseTimeout(Duration.ofSeconds(3));
    }

    @Bean("defaultProcessorWebClient")
    public WebClient defaultProcessorWebClient(HttpClient httpClient) {
        String defaultUrl = System.getenv("PAYMENT_PROCESSOR_DEFAULT_URL").trim();
        return WebClient.builder()
            .baseUrl(defaultUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(256 * 1024);
                configurer.defaultCodecs().enableLoggingRequestDetails(false);
            })
            .build();
    }

    @Bean("fallbackProcessorWebClient")
    public WebClient fallbackProcessorWebClient(HttpClient httpClient) {
        String fallbackUrl = System.getenv("PAYMENT_PROCESSOR_FALLBACK_URL").trim();
        return WebClient.builder()
            .baseUrl(fallbackUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(256 * 1024);
                configurer.defaultCodecs().enableLoggingRequestDetails(false);
            })
            .build();
    }
}