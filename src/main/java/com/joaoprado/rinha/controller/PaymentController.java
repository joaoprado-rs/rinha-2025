package com.joaoprado.rinha.controller;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.dto.PaymentSummaryResponse;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.service.PaymentSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;

@RestController
public class PaymentController {

    private static final Logger logger = Logger.getLogger(PaymentController.class.getName());

    PaymentQueue paymentQueue;
    PaymentSummaryService paymentSummaryService;

    public PaymentController(PaymentQueue paymentQueue, PaymentSummaryService paymentSummaryService) {
        this.paymentQueue = paymentQueue;
        this.paymentSummaryService = paymentSummaryService;
    }

    @PostMapping("/payments")
    public Mono<ResponseEntity<Void>> enqueuePayment(@RequestBody PaymentRequest paymentRequest) {
        return Mono.fromCallable(() -> {
            PaymentRequest enriched = paymentRequest.withRequestedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            paymentQueue.enqueue(enriched);
            return ResponseEntity.accepted().<Void>build();
        })
        .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @GetMapping("/payments-summary")
    public Mono<ResponseEntity<PaymentSummaryResponse>> getPaymentSummary(@RequestParam Map<String, String> params) {
        String from = params.get("from");
        String to = params.get("to");

        return paymentSummaryService.retrievePaymentSummaryReactive(from, to)
            .map(ResponseEntity::ok)
            .doOnError(ex -> logger.warning("Error in payments-summary: " + ex.getMessage()))
            .onErrorReturn(ResponseEntity.badRequest().build());
    }
}