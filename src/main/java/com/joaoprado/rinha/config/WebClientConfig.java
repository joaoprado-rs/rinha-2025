package com.joaoprado.rinha.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
  public WebClient defaultWebClient;
  public WebClient fallbackWebClient;

  @Bean
  public WebClient defaultWebClient() {
    return builder.baseUrl("http://payment-processor-default:8080").build();
  }
}
