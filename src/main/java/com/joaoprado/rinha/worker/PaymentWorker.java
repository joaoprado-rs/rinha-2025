package com.joaoprado.rinha.worker;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.service.HealthCheckerService;
import com.joaoprado.rinha.service.PaymentService;
import com.joaoprado.rinha.service.RedisService;
import jakarta.annotation.PostConstruct;
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
    int threadCount = Runtime.getRuntime().availableProcessors();
    executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      logger.log(Level.INFO, "Creating executor for thread " + i);

      executor.submit(() -> {
        PaymentRequest request = queue.dequeue();
        executePaymentRequest(request);
      });
    }
  }


  public void executePaymentRequest(PaymentRequest message) {
    logger.log(Level.INFO, "Executing payment: " + message.correlationId());
    try {
      paymentService.execute(message, healthChecker.isDefaultHealthy());
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Error: " + ex.getMessage(), ex);
      queue.enqueue(message);
    }
  }

  public void handleListPayments(List<PaymentRequest> messages) {
    for (PaymentRequest message : messages) {
      try {
        paymentService.execute(message, healthChecker.isDefaultHealthy());
      } catch (Exception ex) {
        logger.log(Level.SEVERE, "Error: " + ex.getMessage(), ex);
        if (true) {
          logger.log(Level.WARNING, "Payment is being requeued." + ex.getMessage(), ex);
        }
      }
    }
  }

  public void registerPaymentsSummary(PaymentRequest message) {

  }
}
