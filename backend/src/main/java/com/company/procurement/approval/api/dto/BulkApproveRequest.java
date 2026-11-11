package com.company.procurement.approval.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkApproveRequest(
    @NotEmpty(message = "ids is required")
        @Size(max = 20, message = "at most 20 steps can be approved at once")
        List<UUID> ids,
    @Size(max = 2000, message = "comment must be at most 2000 characters") String comment) {}
