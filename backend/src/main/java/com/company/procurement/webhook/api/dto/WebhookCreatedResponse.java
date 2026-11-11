package com.company.procurement.webhook.api.dto;

public record WebhookCreatedResponse(
    WebhookSubscriptionResponse subscription, String signingSecret) {}
