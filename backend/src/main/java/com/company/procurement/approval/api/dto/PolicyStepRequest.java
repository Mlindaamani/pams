package com.company.procurement.approval.api.dto;

import com.company.procurement.approval.domain.ApproverType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PolicyStepRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,
    @NotNull(message = "approverType is required") ApproverType approverType,
    @Size(max = 50, message = "approverRole must be at most 50 characters") String approverRole,
    UUID approverUserId,
    @Min(value = 1, message = "slaHours must be positive") int slaHours,
    @Min(value = 1, message = "stage must be positive") Integer stage) {}
