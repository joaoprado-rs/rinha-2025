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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PaymentWorker {

  private static final Logger logger = Logger.getLogger(PaymentWorker.class.getName());

  private static final int WORKER_THREADS = 16; // Aumentado de 8 para 16
  private static final int MAX_CONCURRENT = 800; // Aumentado de 500 para 800

  private final PaymentQueue queue;
  private final PaymentService paymentService;
  private final RedisService redisService;
  private final HealthCheckerService healthChecker;
  @Qualifier("metricsExecutor") Executor metricsExecutor;

  private ExecutorService executor;
  private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

  private volatile boolean shutdown = false;

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
  public void consumeQueue() {
    executor = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
      Thread t = new Thread(r, "payment-worker");
      t.setDaemon(true);
      return t;
    });

    for (int i = 0; i < WORKER_THREADS; i++) {
      final int workerId = i;
      executor.submit(() -> workerLoop(workerId));
    }

    logger.log(Level.INFO, "Started {0} workers, max concurrent: {1}",
        new Object[]{WORKER_THREADS, MAX_CONCURRENT});
  }

  private void workerLoop(int workerId) {
    logger.log(Level.INFO, "Worker {0} starting", workerId);

    while (!shutdown && !Thread.currentThread().isInterrupted()) {
      try {
        // Reduzir timeout do semáforo para ser mais agressivo
        if (!semaphore.tryAcquire(1, TimeUnit.MILLISECONDS)) {
          continue;
        }
        PaymentRequest request = queue.dequeue();
        if (request != null) {
          processPayment(request);
        } else {
          semaphore.release();
          // Eliminar completamente o sleep para máxima responsividade
          Thread.yield(); // Apenas yield para dar chance a outras threads
        }

      } catch (PaymentQueueException ex) {
        semaphore.release();
        if (ex.getMessage().contains("interrupted") || shutdown) {
          break;
        }

      } catch (InterruptedException e) {
        semaphore.release();
        Thread.currentThread().interrupt();
        break;

      } catch (Exception ex) {
        semaphore.release();
        logger.log(Level.SEVERE, "Worker {0} error: {1}",
            new Object[]{workerId, ex.getMessage()});
      }
    }

    logger.log(Level.INFO, "Worker {0} finished", workerId);
  }

  private void processPayment(PaymentRequest request) {
    healthChecker.getBestProcessor()
        .thenCompose(processor ->
            paymentService.execute(request, processor)
                .thenAccept(result -> {
                    registerPaymentsSummary(request, processor);
                })
                .exceptionally(ex -> {
                    PaymentProcessor fallback = (processor == PaymentProcessor.DEFAULT)
                        ? PaymentProcessor.FALLBACK : PaymentProcessor.DEFAULT;
                    return paymentService.execute(request, fallback)
                        .thenAccept(result -> registerPaymentsSummary(request, fallback))
                        .exceptionally(fallbackEx -> {
                            logger.log(Level.WARNING, "Both processors failed for payment {0}: primary={1}, fallback={2}",
                                new Object[]{request.correlationId(), ex.getMessage(), fallbackEx.getMessage()});
                            return null;
                        })
                        .join(); // Garante que o fallback seja processado sincronamente
                })
        )
        .whenComplete((result, ex) -> {
            semaphore.release(); // ✅ SEMPRE libera o semáforo
        });
  }

  private void registerPaymentsSummary(PaymentRequest request, PaymentProcessor processor) {
    // Registro síncrono para evitar inconsistências
    try {
      redisService.incrementPaymentCounter(processor, request);
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Failed to register payment summary for {0}: {1}",
          new Object[]{request.correlationId(), ex.getMessage()});
    }
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;

    if (executor != null) {
      logger.log(Level.INFO, "Shutting down workers...");
      executor.shutdownNow();

      try {
        if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
          logger.log(Level.WARNING, "Some threads did not terminate gracefully");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}