package com.company.procurement.webhook.api.dto;

public record WebhookSecretResponse(String signingSecret, long version) {}
