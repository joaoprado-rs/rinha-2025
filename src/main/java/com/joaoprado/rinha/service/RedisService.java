package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.dto.PaymentSummaryResponse;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
  public final RedisAsyncCommands<String, String> redis;
  private static final String HEALTH_KEY = "health:status";

  public RedisService(StatefulRedisConnection<String, String> redis) {
    this.redis = redis.async();
  }

  public void setHealthStatus(PaymentProcessor processor, boolean isHealthy, long responseTime) {
    String processorName = processor.toString().toLowerCase();
    Map<String, String> healthData = Map.of(
            processorName + ":healthy", String.valueOf(isHealthy),
            processorName + ":responseTime", String.valueOf(responseTime)
    );
    redis.hset(HEALTH_KEY, healthData);
  }

  public CompletableFuture<Boolean> isHealthy(PaymentProcessor processor) {
    String processorName = processor.toString().toLowerCase();
    return redis.hget(HEALTH_KEY, processorName + ":healthy")
            .toCompletableFuture()
            .thenApply("true"::equals)
            .exceptionally(ex -> false);
  }

  public void incrementPaymentCounter(PaymentProcessor paymentProcessor, PaymentRequest paymentRequest) {
    String key = "payment:" + paymentRequest.correlationId();
    Map<String, String> paymentData = Map.of(
        "processor", paymentProcessor.toString().toUpperCase(),
        "amount", String.valueOf(paymentRequest.amount()),
        "timestamp", String.valueOf(Instant.parse(paymentRequest.requestedAt()).toEpochMilli())
    );
    redis.hset(key, paymentData);
    long timestamp = Instant.parse(paymentRequest.requestedAt()).toEpochMilli();
    redis.zadd("payments:by_timestamp", (double) timestamp, paymentRequest.correlationId().toString());
  }


  public PaymentSummaryResponse getPaymentSummary(String from, String to) throws Exception {
    long startTime = Instant.parse(from).toEpochMilli();
    long endTime = Instant.parse(to).toEpochMilli();
    Map<String, PaymentSummaryResponse.PaymentStats> tuple = generatePaymentStats(startTime, endTime);
    return new PaymentSummaryResponse(
        tuple.get("default"),
        tuple.get("fallback")
    );
  }

  public Map<String, PaymentSummaryResponse.PaymentStats> generatePaymentStats(long startMillis, long endMillis) throws Exception {
    Map<String, PaymentSummaryResponse.PaymentStats> result = new HashMap<>();

    // Timeout mais realista
    List<String> ids = redis.zrangebyscore("payments:by_timestamp", startMillis, endMillis)
        .get(2, TimeUnit.SECONDS);

    if (ids.isEmpty()) {
      result.put("default", new PaymentSummaryResponse.PaymentStats(0, 0.0));
      result.put("fallback", new PaymentSummaryResponse.PaymentStats(0, 0.0));
      return result;
    }

    // Processa em batches para evitar sobrecarga
    int batchSize = 1000;
    int countDefault = 0;
    double amountDefault = 0;
    int countFallback = 0;
    double amountFallback = 0;

    for (int i = 0; i < ids.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, ids.size());
      List<String> batch = ids.subList(i, endIndex);
      
      // Usar pipeline para operações em batch
      List<RedisFuture<Map<String, String>>> futures = batch.stream()
          .map(id -> redis.hgetall("payment:" + id))
          .toList();

      // Aguarda todas as operações do batch com timeout razoável
      CompletableFuture.allOf(futures.stream()
          .map(RedisFuture::toCompletableFuture)
          .toArray(CompletableFuture[]::new))
          .get(3, TimeUnit.SECONDS);

      // Processa resultados do batch
      for (RedisFuture<Map<String, String>> future : futures) {
        try {
          Map<String, String> data = future.get(100, TimeUnit.MILLISECONDS);

          if (data == null || data.isEmpty()) {
            continue;
          }

          String processor = data.get("processor");
          String amountStr = data.get("amount");
          
          if (processor != null && amountStr != null) {
            double amount = Double.parseDouble(amountStr);
            if ("DEFAULT".equals(processor)) {
              countDefault++;
              amountDefault += amount;
            } else if ("FALLBACK".equals(processor)) {
              countFallback++;
              amountFallback += amount;
            }
          }
        } catch (Exception ex) {
          // Log e continua processando outros itens
        }
      }
    }

    result.put("default", new PaymentSummaryResponse.PaymentStats(countDefault, amountDefault));
    result.put("fallback", new PaymentSummaryResponse.PaymentStats(countFallback, amountFallback));
    return result;
  }
}
