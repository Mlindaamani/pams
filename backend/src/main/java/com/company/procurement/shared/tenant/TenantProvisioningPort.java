package com.company.procurement.shared.tenant;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only tenant administrator provisioning information. */
public interface TenantProvisioningPort {

  Map<UUID, ProvisioningState> states(Collection<UUID> organizationIds);

  Optional<NotificationContact> notificationContact(UUID organizationId);

  void ensurePending(UUID organizationId, String email, String name);

  record ProvisioningState(String adminEmail, String adminName, String status, String lastError) {}

  record NotificationContact(String email, String name) {}
}
