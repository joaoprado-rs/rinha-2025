package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentSummaryResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentSummaryService {

    private final RedisService redisService;

    PaymentSummaryService(RedisService redisService) {
        this.redisService = redisService;
    }

    public Mono<PaymentSummaryResponse> retrievePaymentSummaryReactive(String from, String to) {
        return redisService.getPaymentSummaryReactive(from, to);
    }

    public PaymentSummaryResponse retrievePaymentSummary(String from, String to) throws Exception {
        return redisService.getPaymentSummary(from, to);
    }
}
