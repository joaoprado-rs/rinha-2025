package com.joaoprado.rinha.service;

import com.joaoprado.rinha.dto.HealthCheckerResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class HealthCheckerService {

    //TODO: Cache these values with caffeine.
    private WebClient webClient = WebClient.create();
    private boolean isDefaultHealthy;
    private long lastTimeChecked;

    public synchronized boolean isDefaultHealthy() {
        long now = System.currentTimeMillis();
        if (now - lastTimeChecked >= TimeUnit.SECONDS.toMillis(5)) {
            refresh();
            lastTimeChecked = now;
        }
        return isDefaultHealthy;
    }

    public void refresh() {
        try {
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
