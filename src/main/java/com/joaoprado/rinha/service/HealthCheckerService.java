package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.HealthCheckerResponse;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class HealthCheckerService {
    private static final Logger logger = Logger.getLogger(HealthCheckerService.class.getName());

    private static final String SERVICE_HEALTH = "/payments/service-health";
    private final WebClient defaultClient;
    private final WebClient fallbackClient;
    private final RedisService redisService;

    // Cache local para reduzir consultas ao Redis
    private final ConcurrentHashMap<PaymentProcessor, AtomicBoolean> healthCache = new ConcurrentHashMap<>();
    private volatile PaymentProcessor lastBestProcessor = PaymentProcessor.DEFAULT;

    public HealthCheckerService(
            @Qualifier("defaultProcessorWebClient") WebClient defaultClient,
            @Qualifier("fallbackProcessorWebClient") WebClient fallbackClient,
            RedisService redisService
    ) {
        this.defaultClient = defaultClient;
        this.fallbackClient = fallbackClient;
        this.redisService = redisService;

        // Inicializa cache
        healthCache.put(PaymentProcessor.DEFAULT, new AtomicBoolean(true));
        healthCache.put(PaymentProcessor.FALLBACK, new AtomicBoolean(true));
    }

    public reactor.core.publisher.Mono<PaymentProcessor> getBestProcessor() {
        if (healthCache.get(lastBestProcessor).get()) {
            return reactor.core.publisher.Mono.just(lastBestProcessor);
        }

        return redisService.isHealthyReactive(PaymentProcessor.DEFAULT)
            .map(defaultHealthy -> {
                PaymentProcessor best = defaultHealthy ? PaymentProcessor.DEFAULT : PaymentProcessor.FALLBACK;
                lastBestProcessor = best;
                return best;
            });
    }

    @Scheduled(fixedRate = 10000)
    public void refresh() {
        String lockKey = "health-check-lock";
        String lockValue = java.util.UUID.randomUUID().toString();

        redisService.redis.set(lockKey, lockValue,
                io.lettuce.core.SetArgs.Builder.nx().px(8000)) // Lock por 8s
                .thenAccept(result -> {
                    if ("OK".equals(result)) {
                        // Log menos verboso
                        logger.log(Level.FINE, "Performing health check refresh");
                        refreshProcessor(PaymentProcessor.DEFAULT);
                        refreshProcessor(PaymentProcessor.FALLBACK);
                    }
                    // Remove log quando não consegue lock para reduzir ruído
                })
                .exceptionally(ex -> {
                    logger.log(Level.WARNING, "Health check lock failed: {0}", ex.getMessage());
                    return null;
                });
    }

    private void refreshProcessor(PaymentProcessor processor) {
        WebClient client = (processor == PaymentProcessor.DEFAULT) ? defaultClient : fallbackClient;
        client.get()
                .uri(SERVICE_HEALTH)
                .retrieve()
                .bodyToMono(HealthCheckerResponse.class)
                .timeout(Duration.ofMillis(1000)) // Aumentado para 1s para ser mais tolerante
                .subscribe(
                        response -> {
                            boolean healthy = response != null && !response.failing();
                            long minTime = response != null ? response.minResponseTime() : Long.MAX_VALUE;

                            // Atualiza cache local
                            healthCache.get(processor).set(healthy);

                            // Atualiza Redis
                            redisService.setHealthStatus(processor, healthy, minTime);
                        },
                        error -> {
                            // Marca como não saudável no cache
                            healthCache.get(processor).set(false);
                            redisService.setHealthStatus(processor, false, Long.MAX_VALUE);
                        }
                );
    }
}
