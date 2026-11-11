package com.company.procurement.shared.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkIdsRequest(
    @NotEmpty(message = "ids is required") @Size(max = 100, message = "at most 100 ids are allowed")
        List<UUID> ids) {}
