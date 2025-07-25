package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.PaymentRequest;
import com.joaoprado.rinha.queue.PaymentQueue;
import com.joaoprado.rinha.worker.PaymentWorker;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ProcessorDispatcher {

    private static final Logger logger = Logger.getLogger(ProcessorDispatcher.class.getName());
    private final PaymentQueue queue;
    private final PaymentWorker worker;

    public ProcessorDispatcher(PaymentQueue queue, PaymentWorker worker) {
        this.queue = queue;
        this.worker = worker;
    }

    @PostConstruct
    public void startDispatcher() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    logger.log(Level.INFO, "Waiting for queue to process");
                    PaymentRequest message =  queue.dequeue();
                    logger.log(Level.INFO, "Processing request: " + message);
                    worker.executePaymentFromQueue(message);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Error: " + ex.getMessage(), ex);
                    ex.printStackTrace();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }




    public void executePayments(PaymentRequest message) {

    }
}
