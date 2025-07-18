package com.joaoprado.rinha.queue;

import com.joaoprado.rinha.dto.PaymentRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class PaymentQueue {
    BlockingQueue<PaymentRequest> queue = new LinkedBlockingQueue<>();

   public void enqueue(PaymentRequest paymentRequest) {
      try {
         queue.add(paymentRequest);
      } catch (Exception e) {
          throw new PaymentQueueException(e.getMessage());
      }
   }

   public PaymentRequest dequeue() {
       try {
           return queue.take();
       } catch (Exception e) {
           throw new PaymentQueueException(e.getMessage());
       }
   }
}
