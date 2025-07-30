package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.dto.PaymentSummaryResponse;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Service
public class RedisService {
  public final RedisAsyncCommands<String, String> redis;
  private static final String HEALTH_KEY = "health:status";

  public RedisService(StatefulRedisConnection redis) {
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
            .thenApply(s -> "true".equals(s))
            .exceptionally(ex -> false);
  }

  public CompletableFuture<Long> getMinResponseTime(PaymentProcessor processor) {
    String processorName = processor.toString().toLowerCase();
    return redis.hget(HEALTH_KEY, processorName + ":responseTime")
            .toCompletableFuture()
            .thenApply(s -> s != null ? Long.parseLong(s) : Long.MAX_VALUE)
            .exceptionally(ex -> Long.MAX_VALUE);
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

    // Timeout mais agressivo para evitar travamentos
    List<String> ids = redis.zrangebyscore("payments:by_timestamp", (double) startMillis, (double) endMillis)
        .get(500, TimeUnit.MILLISECONDS); // Reduzido de 1s para 500ms

    // Limitar processamento para evitar sobrecarga
    if (ids.size() > 10000) {
      ids = ids.subList(0, 10000); // Processa no máximo 10k pagamentos
    }

    List<CompletableFuture<Map<String, String>>> futures = ids.stream()
        .map(id -> redis.hgetall("payment:" + id).toCompletableFuture())
        .collect(Collectors.toList());

    int countDefault = 0;
    double amountDefault = 0;
    int countFallback = 0;
    double amountFallback = 0;

    // Timeout por future também
    for (int i = 0; i < ids.size(); i++) {
      try {
        Map<String, String> data = futures.get(i).get(50, TimeUnit.MILLISECONDS); // Timeout agressivo por item

        if (data == null || data.isEmpty()) {
          continue;
        }

        if ("DEFAULT".equals(data.get("processor"))) {
          countDefault++;
          amountDefault += Double.parseDouble(data.get("amount"));
        } else {
          countFallback++;
          amountFallback += Double.parseDouble(data.get("amount"));
        }
      } catch (TimeoutException ex) {
        // Skip este pagamento se demorar muito
        continue;
      }
    }
    result.put("default", new PaymentSummaryResponse.PaymentStats(countDefault, amountDefault));
    result.put("fallback", new PaymentSummaryResponse.PaymentStats(countFallback, amountFallback));
    return result;
  }
}
