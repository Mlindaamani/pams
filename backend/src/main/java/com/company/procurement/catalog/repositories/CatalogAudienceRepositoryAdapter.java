package com.company.procurement.catalog.repositories;

import com.company.procurement.catalog.domain.CatalogFavorite;
import com.company.procurement.catalog.domain.CatalogFavoriteId;
import com.company.procurement.catalog.domain.CatalogItemDepartment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogAudienceRepositoryAdapter implements CatalogAudienceRepository {

  private final JpaCatalogItemDepartmentRepository departments;
  private final JpaCatalogFavoriteRepository favorites;

  public CatalogAudienceRepositoryAdapter(
      JpaCatalogItemDepartmentRepository departments, JpaCatalogFavoriteRepository favorites) {
    this.departments = departments;
    this.favorites = favorites;
  }

  @Override
  public void replaceDepartments(
      UUID organizationId, UUID catalogItemId, Collection<UUID> departmentIds) {
    departments.deleteByCatalogItem(organizationId, catalogItemId);
    if (departmentIds == null || departmentIds.isEmpty())
      return;
    departments.saveAll(
        new LinkedHashSet<>(departmentIds)
            .stream()
            .map(
                departmentId -> new CatalogItemDepartment(organizationId, catalogItemId, departmentId))
            .toList());
  }

  @Override
  public List<UUID> departmentIdsForItem(UUID organizationId, UUID catalogItemId) {
    return departments.findByIdCatalogItemIdOrderByIdDepartmentIdAsc(catalogItemId).stream()
        .filter(item -> item.getOrganizationId().equals(organizationId))
        .map(item -> item.getId().getDepartmentId())
        .toList();
  }

  @Override
  public Map<UUID, List<UUID>> departmentIdsForItems(
      UUID organizationId, Collection<UUID> catalogItemIds) {
    Map<UUID, List<UUID>> result = new HashMap<>();
    if (catalogItemIds == null || catalogItemIds.isEmpty())
      return result;
    for (CatalogItemDepartment item : departments.findByIdCatalogItemIdIn(catalogItemIds)) {
      if (!item.getOrganizationId().equals(organizationId))
        continue;
      result
          .computeIfAbsent(item.getId().getCatalogItemId(), ignored -> new ArrayList<>())
          .add(item.getId().getDepartmentId());
    }
    return result;
  }

  @Override
  public Set<UUID> itemIdsForDepartment(UUID organizationId, UUID departmentId) {
    return departments.findByOrganizationIdAndIdDepartmentId(organizationId, departmentId).stream()
        .map(item -> item.getId().getCatalogItemId())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Set<UUID> favoriteItemIds(UUID organizationId, UUID userProfileId) {
    return favorites.findByIdUserProfileIdOrderByIdCatalogItemIdAsc(userProfileId).stream()
        .filter(item -> item.getOrganizationId().equals(organizationId))
        .map(item -> item.getId().getCatalogItemId())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public void favorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    CatalogFavoriteId id = new CatalogFavoriteId(userProfileId, catalogItemId);
    if (!favorites.existsById(id)) {
      favorites.save(new CatalogFavorite(organizationId, userProfileId, catalogItemId));
    }
  }

  @Override
  public void unfavorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    favorites.deleteByIdUserProfileIdAndIdCatalogItemId(userProfileId, catalogItemId);
  }
}
