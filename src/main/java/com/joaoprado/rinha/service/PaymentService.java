package com.joaoprado.rinha.service;

import com.joaoprado.rinha.config.WebClientConfig;
import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Service
public class PaymentService {

  private static final Logger logger = Logger.getLogger(PaymentService.class.getName());

  private final String PAYMENTS = "/payments";

  private final WebClient defaultClient;
  private final WebClient fallbackClient;

  public PaymentService(
      @Qualifier("defaultProcessorWebClient") WebClient defaultClient,
      @Qualifier("fallbackProcessorWebClient") WebClient fallbackClient) {
    this.defaultClient = defaultClient;
    this.fallbackClient = fallbackClient;
  }

  public CompletableFuture<Void> execute(PaymentRequest paymentRequest, PaymentProcessor processor) {
    WebClient client = PaymentProcessor.DEFAULT.equals(processor) ? defaultClient : fallbackClient;

    return client
        .post()
        .uri(PAYMENTS)
        .body(Mono.just(paymentRequest), PaymentRequest.class)
        .retrieve()
        .toBodilessEntity()
        .timeout(Duration.ofMillis(800))
        .then()
        .toFuture();
  }
}
