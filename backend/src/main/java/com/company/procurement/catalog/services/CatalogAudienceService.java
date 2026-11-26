package com.company.procurement.catalog.services;

import com.company.procurement.catalog.domain.CatalogDepartmentScope;
import com.company.procurement.catalog.repositories.CatalogAudienceRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains catalog visibility by department and favorites. */
@Service
public class CatalogAudienceService {

  private final CatalogAudienceRepository audiences;

  public CatalogAudienceService(CatalogAudienceRepository audiences) {
    this.audiences = audiences;
  }

  @Transactional
  public void replace(
      UUID organizationId,
      UUID catalogItemId,
      CatalogDepartmentScope scope,
      List<UUID> departmentIds) {
    audiences.replaceDepartments(
        organizationId,
        catalogItemId,
        scope == CatalogDepartmentScope.SELECTED && departmentIds != null
            ? departmentIds
            : List.of());
  }

  @Transactional(readOnly = true)
  public List<UUID> forItem(UUID organizationId, UUID catalogItemId) {
    return audiences.departmentIdsForItem(organizationId, catalogItemId);
  }

  @Transactional(readOnly = true)
  public Map<UUID, List<UUID>> forItems(UUID organizationId, Collection<UUID> itemIds) {
    return audiences.departmentIdsForItems(organizationId, itemIds);
  }

  @Transactional(readOnly = true)
  public Set<UUID> itemIdsForDepartment(UUID organizationId, UUID departmentId) {
    return audiences.itemIdsForDepartment(organizationId, departmentId);
  }

  @Transactional(readOnly = true)
  public Set<UUID> favoriteItemIds(UUID organizationId, UUID userProfileId) {
    return audiences.favoriteItemIds(organizationId, userProfileId);
  }

  @Transactional
  public void favorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    audiences.favorite(organizationId, userProfileId, catalogItemId);
  }

  @Transactional
  public void unfavorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    audiences.unfavorite(organizationId, userProfileId, catalogItemId);
  }

  @Transactional(readOnly = true)
  public Set<UUID> visibleForUser(UUID organizationId, UUID departmentId, UUID userProfileId) {
    Set<UUID> ids = new LinkedHashSet<>(itemIdsForDepartment(organizationId, departmentId));
    ids.addAll(favoriteItemIds(organizationId, userProfileId));
    return ids;
  }
}
