package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PaymentService {

    private static final Logger logger = Logger.getLogger(PaymentService.class.getName());

    private final WebClient defaultClient;
    private final WebClient fallbackClient;
    private final HealthCheckerService healthChecker;

    public PaymentService(WebClient.Builder builder, HealthCheckerService healthChecker) {
        this.defaultClient = builder.baseUrl("http://payment-processor-default:8080").build();
        this.fallbackClient = builder.baseUrl("http://payment-processor-fallback:8080").build();
        this.healthChecker = healthChecker;
    }

    public void execute(PaymentRequest paymentRequest) {
        logger.log(Level.INFO, "Executing payment: " + paymentRequest);
        logger.log(Level.INFO, "Checking if it is healthy...");
        boolean isHealthy = healthChecker.isDefaultHealthy();
        if (isHealthy) {
            logger.log(Level.INFO, "HEALTHY");
            defaultClient
                    .post()
                    .uri("/payments")
                    .body(Mono.just(paymentRequest), ClientResponse.class);
        } else {
            logger.log(Level.INFO, "NOT HEALTHY");
            fallbackClient
                    .post()
                    .uri("/payments")
                    .body(Mono.just(paymentRequest), ClientResponse.class);
        }
    }
}
