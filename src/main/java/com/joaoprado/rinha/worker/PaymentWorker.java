package com.joaoprado.rinha.worker;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.service.HealthCheckerService;
import com.joaoprado.rinha.service.PaymentService;
import com.joaoprado.rinha.service.RedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PaymentWorker {

  private static final Logger logger = Logger.getLogger(PaymentWorker.class.getName());

  private final PaymentQueue queue;
  private final PaymentService paymentService;
  private final RedisService redisService;
  private final HealthCheckerService healthChecker;
  private ExecutorService executor;


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
  public void consumeQueue() {
    int threadCount = Runtime.getRuntime().availableProcessors() * 4;
    executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
      logger.log(Level.INFO, "Creating executor for thread " + i);
      executor.submit(() -> {
        while (!Thread.currentThread().isInterrupted()) {
          try {
            PaymentRequest request = queue.dequeue();
            if (request != null) {
              executePaymentRequest(request);
            }
          } catch (Exception ex) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      });
    }
  }

  @PreDestroy
  public void shutdown() {
    if (executor != null) {
      executor.shutdownNow();
      logger.log(Level.INFO, "Shutting down worker...");
    }
  }

  public void executePaymentRequest(PaymentRequest message) {
    logger.log(Level.INFO, "Executing payment: " + message.correlationId());
    try {
      PaymentProcessor processor = determineProcessor();
      paymentService.execute(message, processor);
      registerPaymentsSummary(message, processor);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Error: " + ex.getMessage(), ex);
      queue.enqueue(message);
    }
  }

  public void registerPaymentsSummary(PaymentRequest message, PaymentProcessor processor) {
    redisService.incrementPaymentCounter(processor, message);
  }

  public PaymentProcessor determineProcessor() {
    try {
      if (healthChecker.isDefaultHealthy()) {
        return PaymentProcessor.DEFAULT;
      } else {
        return PaymentProcessor.FALLBACK;
      }
    } catch (Exception ex) {
      return PaymentProcessor.FALLBACK;
    }
  }

}
