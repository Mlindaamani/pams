package com.company.procurement.shared.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pams.webhooks")
public record WebhookProperties(
    @Min(1) int maxAttempts,
    @Min(1) int batchSize,
    @Min(100) long connectTimeoutMs,
    @Min(100) long readTimeoutMs,
    @Min(500) long pollIntervalMs,
    @Min(0) long initialDelayMs) {}
