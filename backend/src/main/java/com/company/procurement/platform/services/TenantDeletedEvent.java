package com.company.procurement.platform.services;

import java.util.List;
import java.util.UUID;

/** Signals committed tenant deletion so external stores can be cleaned up. */
public record TenantDeletedEvent(
    UUID organizationId, List<String> userSubjects, List<String> storageKeys) {}
