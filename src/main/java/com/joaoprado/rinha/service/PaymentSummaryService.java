package com.joaoprado.rinha.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PaymentSummaryService {

    private final WebClient client;

    public PaymentSummaryService(WebClient.Builder builder) {
        this.client = builder.baseUrl("http://localhost:8080").build();
    }
}
