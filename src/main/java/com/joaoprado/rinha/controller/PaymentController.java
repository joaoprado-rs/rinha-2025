package com.joaoprado.rinha.controller;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.queue.PaymentQueue;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    PaymentQueue paymentQueue = new PaymentQueue();

    @PostMapping("/payments")
    public void enqueuePayment(@RequestBody PaymentRequest paymentRequest) {
        try {
            paymentQueue.enqueue(paymentRequest);
            ResponseEntity.accepted().build();
        }  catch (Exception ex) {
            ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/payments-summary")
    public void getPaymentSummary(@RequestParam Map<String, String> params) {
        String from = params.get("from");
        String to = params.get("to");

    }
}