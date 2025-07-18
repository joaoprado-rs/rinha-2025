package com.joaoprado.rinha.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public record PaymentRequest(UUID correlationId,
                             double amount,
                             @JsonIgnore String requestedAt
) {
    public PaymentRequest withRequestedAt(String requestedAt) {
        return new PaymentRequest(this.correlationId, this.amount, requestedAt);
    }
}