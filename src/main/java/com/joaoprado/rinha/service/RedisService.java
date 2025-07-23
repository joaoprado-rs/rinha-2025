package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.dto.PaymentSummaryResponse;
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
    private final RedisAsyncCommands<String, String> redis;

    public RedisService(StatefulRedisConnection redis) {
        this.redis = redis.async();
    }

    public void incrementPaymentCounter(String paymentProcessor, PaymentRequest paymentRequest) {
        String key = "payment:" + paymentRequest.correlationId();
        Map<String, String> paymentData = Map.of(
                "processor", paymentProcessor,
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
        List<String> ids = redis.zrangebyscore("payments:by_timestamp", (double) startMillis, (double) endMillis).get(1, TimeUnit.SECONDS);
        List<CompletableFuture<Map<String, String>>> futures = ids.stream()
                .map(id -> redis.hgetall("payment:" + id).toCompletableFuture())
                .collect(Collectors.toList());
        int countDefault = 0;
        double amountDefault = 0;
        int countFallback = 0;
        double amountFallback = 0;

        for (int i = 0; i < ids.size(); i++) {
            Map<String, String> data = futures.get(i).join();

            if (data == null || data.isEmpty()) {
                continue;
            }

            if ("default".equals(data.get("processor"))) {
                countDefault++;
                amountDefault += Double.parseDouble(data.get("amount"));
            } else {
                countFallback++;
                amountFallback += Double.parseDouble(data.get("amount"));
            }
        }
        result.put("default", new PaymentSummaryResponse.PaymentStats(countDefault, amountDefault));
        result.put("fallback", new PaymentSummaryResponse.PaymentStats(countFallback, amountFallback));
        return result;
    }
}
