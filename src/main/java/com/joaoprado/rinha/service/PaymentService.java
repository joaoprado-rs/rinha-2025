package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.logging.Logger;

@Service
public class PaymentService {

  private static final Logger logger = Logger.getLogger(PaymentService.class.getName());

  private final String PAYMENTS = "/payments";

  private final WebClient defaultClient;
  private final WebClient fallbackClient;
  private final HealthCheckerService healthCheckerService;

  public PaymentService(
      @Qualifier("defaultProcessorWebClient") WebClient defaultClient,
      @Qualifier("fallbackProcessorWebClient") WebClient fallbackClient, HealthCheckerService healthCheckerService) {
    this.defaultClient = defaultClient;
    this.fallbackClient = fallbackClient;
    this.healthCheckerService = healthCheckerService;
  }

  public Mono<Void> execute(PaymentRequest paymentRequest, PaymentProcessor processor) {
    WebClient client = PaymentProcessor.DEFAULT.equals(processor) ? defaultClient : fallbackClient;
    long minResponseTime = healthCheckerService.getMinResponseTime(processor);
    long timeoutValue = Math.min(minResponseTime + 200L, 1000L); // Cap timeout at 1000ms (1s)
    Duration dynamicTimeout = Duration.ofMillis(timeoutValue);

    return client
        .post()
        .uri(PAYMENTS)
        .body(Mono.just(paymentRequest), PaymentRequest.class)
        .retrieve()
        .toBodilessEntity()
        .timeout(dynamicTimeout)
        .retryWhen(Retry.backoff(1, Duration.ofMillis(10))
            .filter(throwable -> !(throwable instanceof WebClientResponseException.BadRequest)))
        .then()
        .doOnError(ex -> logger.fine("Payment failed for " + paymentRequest.correlationId() + ": " + ex.getClass().getSimpleName()));
  }
}
