package com.joaoprado.rinha.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PaymentRequest(UUID correlationId,
                             double amount,
                             @JsonProperty(access = JsonProperty.Access.READ_ONLY) String requestedAt
) {

    @JsonCreator
    public PaymentRequest(
        @JsonProperty("correlationId") UUID correlationId,
        @JsonProperty("amount") double amount
    ) {
        this(correlationId, amount, null);
    }

    public PaymentRequest withRequestedAt(String requestedAt) {
        return new PaymentRequest(this.correlationId, this.amount, requestedAt);
    }
}