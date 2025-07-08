package com.joaoprado.rinha.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PaymentSummaryService {
    private final WebClient client;

    public PaymentSummaryService(WebClient client) {
        this.client = client;
    }
}
