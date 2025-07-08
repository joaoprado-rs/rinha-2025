package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {

    private final WebClient defaultClient;
    private final WebClient fallbackClient;
    private final HealthCheckerService healthChecker;

    public PaymentService(WebClient.Builder builder, HealthCheckerService healthChecker) {
        this.defaultClient = builder.baseUrl("http://payment-processor-default:8080").build();
        this.fallbackClient = builder.baseUrl("http://payment-processor-fallback:8080").build();
        this.healthChecker = healthChecker;
    }

    public void execute(PaymentRequest paymentRequest) {
        boolean isHealthy = healthChecker.isDefaultHealthy();
        if (isHealthy) {
            defaultClient
                    .post()
                    .uri("/payments")
                    .body(Mono.just(paymentRequest), ClientResponse.class);
        } else {
            fallbackClient
                    .post()
                    .uri("/payments")
                    .body(Mono.just(paymentRequest), ClientResponse.class);
        }
    }
}
