package com.company.procurement.organization.infrastructure;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.repositories.OrganizationRepository;
import com.company.procurement.shared.tenant.OrganizationNamePort;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrganizationNameAdapter implements OrganizationNamePort {

  private final OrganizationRepository organizations;

  public OrganizationNameAdapter(OrganizationRepository organizations) {
    this.organizations = organizations;
  }

  @Override
  public Optional<String> name(UUID organizationId) {
    return organizations.findById(organizationId).map(Organization::getName);
  }

  @Override
  public Map<UUID, String> names(Collection<UUID> organizationIds) {
    if (organizationIds == null || organizationIds.isEmpty()) {
      return Map.of();
    }
    return organizations.findAllByIds(organizationIds).stream()
        .collect(Collectors.toMap(Organization::getId, Organization::getName));
  }
}
