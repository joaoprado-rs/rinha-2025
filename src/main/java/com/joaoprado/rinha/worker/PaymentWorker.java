package com.joaoprado.rinha.worker;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.queue.PaymentQueueException;
import com.joaoprado.rinha.service.HealthCheckerService;
import com.joaoprado.rinha.service.PaymentService;
import com.joaoprado.rinha.service.RedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    // CPU em 50% indica que podemos dobrar as threads para melhor utilização
    int threadCount = 32; // Dobrando de 32 para 64 - aproveitar os 50% de CPU ociosos

    logger.log(Level.INFO, "Starting PaymentWorker with " + threadCount + " threads (CPU utilization optimized)");

    executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(() -> {
        logger.log(Level.INFO, "Starting worker thread " + threadId);
        while (!Thread.currentThread().isInterrupted()) {
          try {
            PaymentRequest request = queue.dequeue();
            if (request != null) {
              executePaymentRequest(request);
            }
          } catch (PaymentQueueException ex) {
            if (ex.getMessage().contains("interrupted")) {
              logger.log(Level.INFO, "Worker thread " + threadId + " interrupted gracefully");
              break;
            }
            logger.log(Level.WARNING, "Queue error in thread " + threadId + ": " + ex.getMessage());
          } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error in worker thread " + threadId + ": " + ex.getMessage());
            Thread.currentThread().interrupt();
            break;
          }
        }
        logger.log(Level.INFO, "Worker thread " + threadId + " finished");
      });
    }
  }

  @PreDestroy
  public void shutdown() {
    if (executor != null) {
      logger.log(Level.INFO, "Shutting down worker threads...");
      executor.shutdownNow();
      try {
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          logger.log(Level.WARNING, "Some threads did not terminate gracefully");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public void executePaymentRequest(PaymentRequest message) {
    healthChecker.getBestProcessor()
        .thenCompose(initialProcessor ->
            paymentService.execute(message, initialProcessor)
                .thenAccept(v -> registerPaymentsSummary(message, initialProcessor))
                .exceptionally(ex -> {
                    PaymentProcessor fallbackProcessor = initialProcessor == PaymentProcessor.DEFAULT ?
                        PaymentProcessor.FALLBACK : PaymentProcessor.DEFAULT;
                    paymentService.execute(message, fallbackProcessor)
                        .thenAccept(v -> registerPaymentsSummary(message, fallbackProcessor));
                    return null;
                })
        );
  }

  public void registerPaymentsSummary(PaymentRequest message, PaymentProcessor processor) {
    redisService.incrementPaymentCounter(processor, message);
  }
}
