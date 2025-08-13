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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PaymentWorker {

  private static final Logger logger = Logger.getLogger(PaymentWorker.class.getName());
  private static final int MAX_CONCURRENT = 150;

  private final PaymentQueue queue;
  private final PaymentService paymentService;
  private final RedisService redisService;
  private final HealthCheckerService healthChecker;

  private Disposable subscription;

  public PaymentWorker(
      PaymentQueue queue,
      PaymentService paymentService,
      RedisService redisService,
      HealthCheckerService healthChecker
  ) {
    this.queue = queue;
    this.paymentService = paymentService;
    this.redisService = redisService;
    this.healthChecker = healthChecker;
  }

  @PostConstruct
  public void startReactiveProcessing() {
    subscription = queue.getPaymentStream()
        .flatMap(this::processPaymentReactive, MAX_CONCURRENT)
        .subscribeOn(Schedulers.parallel())
        .subscribe(
            result -> logger.fine("Payment processed successfully"),
            error -> logger.log(Level.SEVERE, "Error in payment stream: " + error.getMessage()),
            () -> logger.info("Payment stream completed")
        );

    logger.log(Level.INFO, "Started reactive payment processing with max concurrent: {0}", MAX_CONCURRENT);
  }

  private reactor.core.publisher.Mono<Void> processPaymentReactive(PaymentRequest request) {
    PaymentProcessor processor = healthChecker.getBestProcessor();
    return paymentService.execute(request, processor)
        .then(Mono.fromRunnable(() -> redisService.incrementPaymentCounter(processor, request)))
        .onErrorResume(ex -> {
            PaymentProcessor fallback = (processor == PaymentProcessor.DEFAULT)
                ? PaymentProcessor.FALLBACK : PaymentProcessor.DEFAULT;
            return paymentService.execute(request, fallback)
                .then(Mono.fromRunnable(() -> redisService.incrementPaymentCounter(fallback, request)))
                .onErrorResume(fallbackEx -> {
                    logger.log(Level.WARNING, "Both processors failed for payment {0}", request.correlationId());
                    return Mono.empty();
                });
        }).then();
  }

  @PreDestroy
  public void shutdown() {
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
      logger.info("Reactive payment processing stopped");
    }
  }
}