package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.HealthCheckerResponse;
import com.joaoprado.rinha.worker.PaymentWorker;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class HealthCheckerService {

    private static final Logger logger = Logger.getLogger(HealthCheckerService.class.getName());

    private WebClient webClient = WebClient.create();
    private volatile boolean isDefaultHealthy = true;
    private final AtomicLong lastTimeChecked = new AtomicLong(0);
    private static final long HEALTH_CHECK_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5);

    public boolean isDefaultHealthy() {

        long now = System.currentTimeMillis();
        long lastCheck = lastTimeChecked.get();

        if (now - lastCheck >= HEALTH_CHECK_INTERVAL_MS) {
            synchronized (this) {
                long currentLastCheck = lastTimeChecked.get();
                if (now - currentLastCheck >= HEALTH_CHECK_INTERVAL_MS) {
                    lastTimeChecked.set(now);
                    refresh();
                    logger.log(Level.INFO, "isDefaultHealthy: " + isDefaultHealthy);
                }
            }
        }
        return isDefaultHealthy;
    }

    public void refresh() {
        try {
            logger.log(Level.INFO, "Refreshing health checker");
            ClientResponse response = webClient
                    .get()
                    .uri("http://payment-processor-default:8080/payments/service-health")
                    .exchange()
                    .block(Duration.ofSeconds(1));
            if (response == null) {
                this.isDefaultHealthy = false;
                return;
            }
            HttpStatusCode status = response.statusCode();
            if (status.is2xxSuccessful()) {
                HealthCheckerResponse body = response.bodyToMono(HealthCheckerResponse.class)
                        .block(Duration.ofSeconds(1));

                if (body != null && !body.failing()) {
                    this.isDefaultHealthy = true;
                } else {
                    this.isDefaultHealthy = false;
                }
            } else {
                this.isDefaultHealthy = false;
            }
        } catch (Exception e) {
            this.isDefaultHealthy = false;
        }
    }
}
