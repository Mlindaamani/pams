package com.company.procurement.platform.application;

import com.company.procurement.organization.domain.OrganizationRepository;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.shared.tenant.TenantAvailabilityPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TenantStatusService implements TenantAvailabilityPort {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final OrganizationRepository organizations;
  private final Clock clock;
  private final ConcurrentHashMap<UUID, CachedStatus> cache = new ConcurrentHashMap<>();

  public TenantStatusService(OrganizationRepository organizations, Clock clock) {
    this.organizations = organizations;
    this.clock = clock;
  }

  @Override
  public boolean isSuspended(UUID organizationId) {
    Instant now = clock.instant();
    CachedStatus cached = cache.get(organizationId);
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.status() == OrganizationStatus.SUSPENDED;
    }
    OrganizationStatus status =
        organizations
            .findById(organizationId)
            .map(organization -> organization.getStatus())
            .orElse(OrganizationStatus.ACTIVE);
    cache.put(organizationId, new CachedStatus(status, now.plus(CACHE_TTL)));
    return status == OrganizationStatus.SUSPENDED;
  }

  public void evict(UUID organizationId) {
    cache.remove(organizationId);
  }

  private record CachedStatus(OrganizationStatus status, Instant expiresAt) {}
}
