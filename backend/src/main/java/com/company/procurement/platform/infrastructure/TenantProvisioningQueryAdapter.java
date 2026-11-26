package com.company.procurement.platform.infrastructure;

import com.company.procurement.organization.repositories.UserProfileRepository;
import com.company.procurement.platform.domain.TenantProvisioning;
import com.company.procurement.platform.repositories.TenantProvisioningRepository;
import com.company.procurement.shared.tenant.TenantProvisioningPort;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantProvisioningQueryAdapter implements TenantProvisioningPort {

  private final TenantProvisioningRepository provisionings;
  private final UserProfileRepository users;
  private final Clock clock;

  public TenantProvisioningQueryAdapter(
      TenantProvisioningRepository provisionings, UserProfileRepository users, Clock clock) {
    this.provisionings = provisionings;
    this.users = users;
    this.clock = clock;
  }

  @Override
  public Map<UUID, ProvisioningState> states(Collection<UUID> organizationIds) {
    if (organizationIds == null || organizationIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, ProvisioningState> states = new HashMap<>();
    for (TenantProvisioning provisioning : provisionings.findByOrganizationIdIn(organizationIds)) {
      states.put(
          provisioning.getOrganizationId(),
          new ProvisioningState(
              provisioning.getAdminEmail(),
              provisioning.getAdminName(),
              provisioning.getStatus().name(),
              provisioning.getLastError()));
    }
    return states;
  }

  @Override
  public Optional<NotificationContact> notificationContact(UUID organizationId) {
    Optional<TenantProvisioning> provisioning = provisionings.findByOrganizationId(organizationId);
    if (provisioning.isPresent() && provisioning.get().getAdminEmail() != null) {
      return Optional.of(
          new NotificationContact(
              provisioning.get().getAdminEmail(), provisioning.get().getAdminName()));
    }
    return users
        .findFirstActiveByOrganizationId(organizationId)
        .map(
            user ->
                new NotificationContact(
                    user.getEmail(),
                    user.getDisplayName() == null ? "Administrator" : user.getDisplayName()));
  }

  @Override
  public void ensurePending(UUID organizationId, String email, String name) {
    if (email == null || email.isBlank()) {
      return;
    }
    provisionings
        .findByOrganizationId(organizationId)
        .orElseGet(
            () ->
                provisionings.save(
                    new TenantProvisioning(
                        organizationId, email, name == null ? "" : name, clock.instant())));
  }
}
