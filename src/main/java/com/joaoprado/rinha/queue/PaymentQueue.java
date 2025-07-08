package com.joaoprado.rinha.queue;

import com.joaoprado.rinha.dto.PaymentRequest;

import java.util.concurrent.BlockingQueue;

public class PaymentQueue {
    BlockingQueue<PaymentRequest> queue;

   public void enqueue(PaymentRequest paymentRequest) {
      try {
         queue.add(paymentRequest);
      } catch (Exception e) {
          throw new PaymentQueueException("Some thing went wrong...");
      }
   }
}
