package com.company.procurement.shared.tenant;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Resolves tenant display names without crossing module boundaries. */
public interface OrganizationNamePort {

  Optional<String> name(UUID organizationId);

  Map<UUID, String> names(Collection<UUID> organizationIds);
}
