package com.company.procurement.platform.services;

import java.util.UUID;

public record TenantAdminInviteRequested(UUID organizationId, String email, String displayName) {}
