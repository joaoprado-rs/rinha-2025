package com.joaoprado.rinha.worker;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.service.HealthCheckerService;
import com.joaoprado.rinha.service.PaymentService;
import com.joaoprado.rinha.service.RedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PaymentWorker {

  private static final Logger logger = Logger.getLogger(PaymentWorker.class.getName());

  private static final int MAX_CONCURRENT = 600;

  private final PaymentQueue queue;
  private final PaymentService paymentService;
  private final RedisService redisService;
  private final HealthCheckerService healthChecker;
  @Qualifier("metricsExecutor") private final Executor metricsExecutor;

  private Disposable subscription;

  public PaymentWorker(
      PaymentQueue queue,
      PaymentService paymentService,
      RedisService redisService,
      HealthCheckerService healthChecker,
      @Qualifier("metricsExecutor") Executor metricsExecutor
  ) {
    this.queue = queue;
    this.paymentService = paymentService;
    this.redisService = redisService;
    this.healthChecker = healthChecker;
    this.metricsExecutor = metricsExecutor;
  }

  @PostConstruct
  public void startReactiveProcessing() {
    subscription = queue.getPaymentStream()
        .flatMap(this::processPaymentReactive, MAX_CONCURRENT)
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
            result -> logger.fine("Payment processed successfully"),
            error -> logger.log(Level.SEVERE, "Error in payment stream: " + error.getMessage()),
            () -> logger.info("Payment stream completed")
        );

    logger.log(Level.INFO, "Started reactive payment processing with max concurrent: {0}", MAX_CONCURRENT);
  }

  private reactor.core.publisher.Mono<Void> processPaymentReactive(PaymentRequest request) {
    return healthChecker.getBestProcessor()
        .flatMap(processor ->
            paymentService.execute(request, processor)
                .doOnSuccess(result -> registerPaymentsSummary(request, processor))
                .onErrorResume(ex -> {
                    PaymentProcessor fallback = (processor == PaymentProcessor.DEFAULT)
                        ? PaymentProcessor.FALLBACK : PaymentProcessor.DEFAULT;
                    return paymentService.execute(request, fallback)
                        .doOnSuccess(result -> registerPaymentsSummary(request, fallback))
                        .onErrorResume(fallbackEx -> {
                            logger.log(Level.FINE, "Both processors failed for payment {0}", request.correlationId());
                            return reactor.core.publisher.Mono.empty();
                        });
                })
        );
  }

  private void registerPaymentsSummary(PaymentRequest request, PaymentProcessor processor) {
    reactor.core.publisher.Mono.fromRunnable(() -> {
      try {
        redisService.incrementPaymentCounter(processor, request);
      } catch (Exception ex) {
      }
    })
    .subscribeOn(Schedulers.fromExecutor(metricsExecutor))
    .subscribe();
  }

  @PreDestroy
  public void shutdown() {
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
      logger.info("Reactive payment processing stopped");
    }
  }
}