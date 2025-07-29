package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.pojo.PaymentProcessor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Service
public class PaymentService {

    private static final Logger logger = Logger.getLogger(PaymentService.class.getName());


    private final String PAYMENTS = "/payments";
    private final WebClient defaultClient;
    private final WebClient fallbackClient;

    public PaymentService(WebClient.Builder builder) {
        this.defaultClient = builder.baseUrl("http://payment-processor-default:8080").build();
        this.fallbackClient = builder.baseUrl("http://payment-processor-fallback:8080").build();
    }

    public CompletableFuture<Void> execute(PaymentRequest paymentRequest, PaymentProcessor processor) {
        WebClient client = PaymentProcessor.DEFAULT.equals(processor) ? defaultClient : fallbackClient;

        return client
                .post()
                .uri(PAYMENTS)
                .body(Mono.just(paymentRequest), PaymentRequest.class)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofMillis(500)) // Timeout mais agressivo
                .then() // Converte para Mono<Void>
                .toFuture()
                .exceptionally(throwable -> {
                    logger.severe("Payment failed: " + paymentRequest.correlationId() + " - " + throwable.getMessage());
                    return null;
                });
    }
}
