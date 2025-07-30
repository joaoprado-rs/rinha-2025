package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.HealthCheckerResponse;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import com.joaoprado.rinha.worker.PaymentWorker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class HealthCheckerService {
    private static final Logger logger = Logger.getLogger(HealthCheckerService.class.getName());

    private static final String SERVICE_HEALTH = "/payments/service-health";
    private final WebClient defaultClient;
    private final WebClient fallbackClient;
    private final RedisService redisService;

    public HealthCheckerService(
            @Qualifier("defaultProcessorWebClient") WebClient defaultClient,
            @Qualifier("fallbackProcessorWebClient") WebClient fallbackClient,
            RedisService redisService
    ) {
        this.defaultClient = defaultClient;
        this.fallbackClient = fallbackClient;
        this.redisService = redisService;
    }

    public CompletableFuture<PaymentProcessor> getBestProcessor() {
        return redisService.isHealthy(PaymentProcessor.DEFAULT)
            .thenApply(defaultHealthy -> defaultHealthy ? PaymentProcessor.DEFAULT : PaymentProcessor.FALLBACK);
    }

    @Scheduled(fixedRate = 5000)
    public void refresh() {

        String lockKey = "health-check-lock";
        String lockValue = java.util.UUID.randomUUID().toString();

        redisService.redis.set(lockKey, lockValue,
                io.lettuce.core.SetArgs.Builder.nx().px(3000))
                .thenAccept(result -> {
                    if ("OK".equals(result)) {
                        logger.info("Acquired health check lock, performing refresh");
                        refreshProcessor(PaymentProcessor.DEFAULT);
                        refreshProcessor(PaymentProcessor.FALLBACK);
                    } else {
                        logger.fine("Another instance is performing health check, skipping");
                    }
                })
                .exceptionally(ex -> {
                    logger.warning("Failed to acquire health check lock: " + ex.getMessage());
                    return null;
                });
    }

    private void refreshProcessor(PaymentProcessor processor) {
        logger.info("Refreshing processor '" + processor + "' at: " + LocalDateTime.now());
        WebClient client = (processor == PaymentProcessor.DEFAULT) ? defaultClient : fallbackClient;
        client.get()
                .uri(SERVICE_HEALTH)
                .retrieve()
                .bodyToMono(HealthCheckerResponse.class)
                .timeout(Duration.ofMillis(500))
                .subscribe(
                        response -> {
                            boolean healthy = response != null && !response.failing();
                            long minTime = response != null ? response.minResponseTime() : Long.MAX_VALUE;
                            redisService.setHealthStatus(processor, healthy, minTime);
                        },
                        error -> redisService.setHealthStatus(processor, false, Long.MAX_VALUE)
                );
    }
}
