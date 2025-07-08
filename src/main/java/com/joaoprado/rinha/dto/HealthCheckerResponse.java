package com.joaoprado.rinha.dto;

public record HealthCheckerResponse(boolean failing, long minResponseTime) {
}
