package com.joaoprado.rinha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("defaultProcessorWebClient")
    public WebClient defaultProcessorWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://payment-processor-default:8080")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }

    @Bean("fallbackProcessorWebClient")
    public WebClient fallbackProcessorWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://payment-processor-fallback:8080")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }

}
