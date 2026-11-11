package com.company.procurement.organization.api.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String keycloakSubject,
    String email,
    String displayName,
    UUID departmentId,
    UUID managerId,
    MoneyDto approvalLimit,
    boolean active,
    boolean hasAvatar,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
