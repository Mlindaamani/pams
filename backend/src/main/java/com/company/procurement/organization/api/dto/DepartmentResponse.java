package com.company.procurement.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
    UUID id, String name, Instant createdAt, Instant updatedAt, long version) {}
