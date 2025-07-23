package com.joaoprado.rinha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentSummaryResponse(
        @JsonProperty("default") PaymentStats defaultPayment,
        @JsonProperty("fallback") PaymentStats fallbackPayment
) {
    public record PaymentStats(
            long totalRequests,
            double totalAmount
    ) {}
}
