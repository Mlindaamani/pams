package com.company.procurement.approval.api.dto;

import jakarta.validation.constraints.Size;

public record ApproveRequest(
    @Size(max = 2000, message = "comment must be at most 2000 characters") String comment) {}
