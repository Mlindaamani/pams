package com.company.procurement.organization.api.dto;

import java.util.UUID;

public record UserOptionResponse(UUID id, String displayName, UUID departmentId) {}
