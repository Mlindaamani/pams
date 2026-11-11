package com.company.procurement.approval.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
    @NotBlank(message = "comment is required")
        @Size(max = 2000, message = "comment must be at most 2000 characters")
        String comment) {}
