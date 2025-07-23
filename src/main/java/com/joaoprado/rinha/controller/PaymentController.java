package com.joaoprado.rinha.controller;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.dto.PaymentSummaryResponse;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.service.PaymentSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
public class PaymentController {

    PaymentQueue paymentQueue;
    PaymentSummaryService paymentSummaryService;

    public PaymentController(PaymentQueue paymentQueue, PaymentSummaryService paymentSummaryService) {
        this.paymentQueue = paymentQueue;
        this.paymentSummaryService = paymentSummaryService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> enqueuePayment(@RequestBody PaymentRequest paymentRequest) {
        try {
            PaymentRequest enriched = paymentRequest.withRequestedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            paymentQueue.enqueue(enriched);
            return ResponseEntity.accepted().build();
        }  catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/payments-summary")
    public ResponseEntity<PaymentSummaryResponse> getPaymentSummary(@RequestParam Map<String, String> params) throws Exception {
        String from = params.get("from");
        String to = params.get("to");
        return ResponseEntity.ok(paymentSummaryService.retrievePaymentSummary(from, to));
    }
}