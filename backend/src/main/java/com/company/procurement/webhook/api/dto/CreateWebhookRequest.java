package com.company.procurement.webhook.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateWebhookRequest(
    @NotBlank @Size(max = 1000) String url,
    @Size(max = 300) String description,
    @NotEmpty Set<String> eventTypes,
    Boolean active) {}
