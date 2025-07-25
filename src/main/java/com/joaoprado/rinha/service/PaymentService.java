package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {

    private final String PAYMENTS = "/payments";
    private final WebClient defaultClient;
    private final WebClient fallbackClient;

    public PaymentService(WebClient.Builder builder) {
        this.defaultClient = builder.baseUrl("http://payment-processor-default:8080").build();
        this.fallbackClient = builder.baseUrl("http://payment-processor-fallback:8080").build();
    }

    public void execute(PaymentRequest paymentRequest, boolean isDefault) {
        if (isDefault) {
            defaultClient
                    .post()
                    .uri(PAYMENTS)
                    .body(Mono.just(paymentRequest), ClientResponse.class);
        } else {
            fallbackClient
                    .post()
                    .uri(PAYMENTS)
                    .body(Mono.just(paymentRequest), ClientResponse.class);
        }
    }
}
