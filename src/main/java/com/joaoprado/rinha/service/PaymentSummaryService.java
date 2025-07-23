package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentSummaryResponse;
import com.joaoprado.rinha.queue.PaymentQueue;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PaymentSummaryService {

    private final RedisService redisService;

    PaymentSummaryService(RedisService redisService) {
        this.redisService = redisService;
    }

    public PaymentSummaryResponse retrievePaymentSummary(String from, String to) throws Exception {
        return redisService.getPaymentSummary(from, to);
    }
}
