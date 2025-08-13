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

  // Novo método 100% reativo para eliminar CompletableFuture
  public reactor.core.publisher.Mono<Boolean> isHealthyReactive(PaymentProcessor processor) {
    String processorName = processor.toString().toLowerCase();
    return reactor.core.publisher.Mono.fromCompletionStage(
        redis.hget(HEALTH_KEY, processorName + ":healthy")
    )
    .map("true"::equals)
    .onErrorReturn(false);
  }

  public void incrementPaymentCounter(PaymentProcessor paymentProcessor, PaymentRequest paymentRequest) {
    String correlationId = paymentRequest.correlationId().toString();
    long timestamp = Instant.parse(paymentRequest.requestedAt()).toEpochMilli();
    String paymentData = paymentProcessor.toString().toUpperCase() + ":" + paymentRequest.amount();

    redis.multi();
    redis.set("payment:" + correlationId, paymentData);
    redis.zadd("payments:by_timestamp", (double) timestamp, correlationId);
    redis.exec();
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

    // Buscar IDs no intervalo de tempo
    List<String> ids = redis.zrangebyscore("payments:by_timestamp", startMillis, endMillis)
        .get(2, TimeUnit.SECONDS);

    if (ids.isEmpty()) {
      result.put("default", new PaymentSummaryResponse.PaymentStats(0, 0.0));
      result.put("fallback", new PaymentSummaryResponse.PaymentStats(0, 0.0));
      return result;
    }

    // Contadores para processamento
    int countDefault = 0;
    double amountDefault = 0;
    int countFallback = 0;
    double amountFallback = 0;

    // Processar em batches para melhor performance
    int batchSize = 1000;
    for (int i = 0; i < ids.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, ids.size());
      List<String> batch = ids.subList(i, endIndex);
      
      // Usar get() para o novo formato otimizado "PROCESSOR:AMOUNT"
      List<RedisFuture<String>> futures = batch.stream()
          .map(id -> redis.get("payment:" + id))
          .toList();

      // Aguardar todas as operações do batch
      CompletableFuture.allOf(futures.stream()
          .map(RedisFuture::toCompletableFuture)
          .toArray(CompletableFuture[]::new))
          .get(3, TimeUnit.SECONDS);

      // Processar resultados do batch com novo formato "PROCESSOR:AMOUNT"
      for (RedisFuture<String> future : futures) {
        try {
          String data = future.get(100, TimeUnit.MILLISECONDS);

          if (data != null && data.contains(":")) {
            String[] parts = data.split(":", 2);
            String processor = parts[0];
            double amount = Double.parseDouble(parts[1]);

            if ("DEFAULT".equals(processor)) {
              countDefault++;
              amountDefault += amount;
            } else if ("FALLBACK".equals(processor)) {
              countFallback++;
              amountFallback += amount;
            }
          }
        } catch (Exception ex) {
          // Continua processando outros itens
        }
      }
    }

    result.put("default", new PaymentSummaryResponse.PaymentStats(countDefault, amountDefault));
    result.put("fallback", new PaymentSummaryResponse.PaymentStats(countFallback, amountFallback));
    return result;
  }

  public reactor.core.publisher.Mono<Map<String, PaymentSummaryResponse.PaymentStats>> generatePaymentStatsReactive(long startMillis, long endMillis) {
    // Converter para pipeline 100% reativo
    return reactor.core.publisher.Mono.fromFuture(
        redis.zrangebyscore("payments:by_timestamp", startMillis, endMillis).toCompletableFuture()
    )
    .flatMap(ids -> {
      if (ids.isEmpty()) {
        Map<String, PaymentSummaryResponse.PaymentStats> emptyResult = Map.of(
            "default", new PaymentSummaryResponse.PaymentStats(0, 0.0),
            "fallback", new PaymentSummaryResponse.PaymentStats(0, 0.0)
        );
        return reactor.core.publisher.Mono.just(emptyResult);
      }

      // Processar todos os IDs em paralelo de forma reativa
      return reactor.core.publisher.Flux.fromIterable(ids)
          .flatMap(id ->
              reactor.core.publisher.Mono.fromFuture(redis.get("payment:" + id).toCompletableFuture())
                  .onErrorReturn("") // Ignorar erros individuais
          )
          .filter(data -> data != null && data.contains(":"))
          .map(data -> {
              String[] parts = data.split(":", 2);
              return new PaymentData(parts[0], Double.parseDouble(parts[1]));
          })
          .collectList()
          .map(this::aggregatePaymentData);
    });
  }

  // Método auxiliar para agregação
  private Map<String, PaymentSummaryResponse.PaymentStats> aggregatePaymentData(java.util.List<PaymentData> payments) {
    int countDefault = 0, countFallback = 0;
    double amountDefault = 0, amountFallback = 0;

    for (PaymentData payment : payments) {
      if ("DEFAULT".equals(payment.processor)) {
        countDefault++;
        amountDefault += payment.amount;
      } else if ("FALLBACK".equals(payment.processor)) {
        countFallback++;
        amountFallback += payment.amount;
      }
    }

    return Map.of(
        "default", new PaymentSummaryResponse.PaymentStats(countDefault, amountDefault),
        "fallback", new PaymentSummaryResponse.PaymentStats(countFallback, amountFallback)
    );
  }

  // Record auxiliar para dados de pagamento
  private record PaymentData(String processor, double amount) {}

  public reactor.core.publisher.Mono<PaymentSummaryResponse> getPaymentSummaryReactive(String from, String to) {
    long startTime = java.time.Instant.parse(from).toEpochMilli();
    long endTime = java.time.Instant.parse(to).toEpochMilli();

    return generatePaymentStatsReactive(startTime, endTime)
        .map(stats -> new PaymentSummaryResponse(
            stats.get("default"),
            stats.get("fallback")
        ));
  }
}
