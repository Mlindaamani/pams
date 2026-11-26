package com.company.procurement.catalog.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistence port for catalog department audiences and favorites. */
public interface CatalogAudienceRepository {

  void replaceDepartments(UUID organizationId, UUID catalogItemId, Collection<UUID> departmentIds);

  List<UUID> departmentIdsForItem(UUID organizationId, UUID catalogItemId);

  Map<UUID, List<UUID>> departmentIdsForItems(UUID organizationId, Collection<UUID> catalogItemIds);

  Set<UUID> itemIdsForDepartment(UUID organizationId, UUID departmentId);

  Set<UUID> favoriteItemIds(UUID organizationId, UUID userProfileId);

  void favorite(UUID organizationId, UUID userProfileId, UUID catalogItemId);

  void unfavorite(UUID organizationId, UUID userProfileId, UUID catalogItemId);
}
