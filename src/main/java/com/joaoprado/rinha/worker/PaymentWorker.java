package com.joaoprado.rinha.worker;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentWorker {

    PaymentService paymentService;

    public PaymentWorker(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void executePaymentFromQueue(PaymentRequest paymentRequest) {
        paymentService.execute(paymentRequest);
    }
}
