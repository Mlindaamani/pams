package com.company.procurement.platform.application;

import java.util.UUID;

public record TenantAdminInviteRequested(UUID organizationId, String email, String displayName) {}
