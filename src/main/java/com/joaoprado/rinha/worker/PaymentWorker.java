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

import java.util.concurrent.CompletableFuture;
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
    int threadCount = Math.max(Runtime.getRuntime().availableProcessors() * 8, 64);
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
    logger.log(Level.INFO, "Executing payment: " + message.correlationId());

    healthChecker.getBestProcessor()
        .thenCompose(initialProcessor ->
            paymentService.execute(message, initialProcessor)
                .thenAccept(v -> registerPaymentsSummary(message, initialProcessor))
                .exceptionally(ex -> {
                    logger.log(Level.WARNING, "Payment failed for processor " + initialProcessor + ". Error: " + ex.getMessage());

//                    PaymentProcessor fallbackProcessor = initialProcessor == PaymentProcessor.DEFAULT ?
//                        PaymentProcessor.FALLBACK : PaymentProcessor.DEFAULT;
//
//                    redisService.isHealthy(fallbackProcessor)
//                        .thenCompose(isHealthy -> {
//                            if (isHealthy) {
//                                logger.log(Level.INFO, "Retrying with processor " + fallbackProcessor);
//                                return paymentService.execute(message, fallbackProcessor)
//                                        .thenAccept(v -> registerPaymentsSummary(message, fallbackProcessor))
//                                        .exceptionally(e -> {
//                                            logger.log(Level.SEVERE, "Payment failed for fallback processor " + fallbackProcessor + ". Error: " + e.getMessage());
//                                            return null;
//                                        });
//                            } else {
//                                logger.log(Level.SEVERE, "Fallback processor " + fallbackProcessor + " is not healthy. Payment failed for " + message.correlationId());
//                                return CompletableFuture.completedFuture(null);
//                            }
//                        });
                    return null;
                })
        )
        .exceptionally(ex -> {
            logger.log(Level.SEVERE, "Failed to determine processor for payment " + message.correlationId() + ": " + ex.getMessage());
            return null;
        });
  }

  public void registerPaymentsSummary(PaymentRequest message, PaymentProcessor processor) {
    redisService.incrementPaymentCounter(processor, message);
  }
}
