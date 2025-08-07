package com.joaoprado.rinha.queue;

import com.joaoprado.rinha.dto.PaymentRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class PaymentQueue {
    private final Sinks.Many<PaymentRequest> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void enqueue(PaymentRequest paymentRequest) {
        Sinks.EmitResult result = sink.tryEmitNext(paymentRequest);
        if (result.isFailure()) {
            throw new PaymentQueueException("Failed to emit payment: " + result);
        }
    }

    public Flux<PaymentRequest> getPaymentStream() {
        return sink.asFlux();
    }
}
