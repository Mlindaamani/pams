package com.company.procurement.catalog.application;

import com.company.procurement.catalog.domain.CatalogDepartmentScope;
import com.company.procurement.shared.actor.CurrentActorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains catalog visibility by department and favorites. */
@Service
public class CatalogAudienceService {

  private final JdbcTemplate jdbc;
  private final CurrentActorProvider actorProvider;

  public CatalogAudienceService(JdbcTemplate jdbc, CurrentActorProvider actorProvider) {
    this.jdbc = jdbc;
    this.actorProvider = actorProvider;
  }

  @Transactional
  public void replace(
      UUID organizationId,
      UUID catalogItemId,
      CatalogDepartmentScope scope,
      List<UUID> departmentIds) {
    jdbc.update(
        "delete from catalog_item_department where organization_id = ? and catalog_item_id = ?",
        organizationId,
        catalogItemId);
    if (scope != CatalogDepartmentScope.SELECTED || departmentIds == null) {
      return;
    }
    new LinkedHashSet<>(departmentIds)
        .forEach(
            departmentId ->
                jdbc.update(
                    """
                    insert into catalog_item_department
                        (organization_id, catalog_item_id, department_id, created_at, created_by)
                    values (?, ?, ?, now(), ?)
                    """,
                    organizationId,
                    catalogItemId,
                    departmentId,
                    actorProvider.currentActor()));
  }

  @Transactional(readOnly = true)
  public List<UUID> forItem(UUID organizationId, UUID catalogItemId) {
    return jdbc.queryForList(
        "select department_id from catalog_item_department where organization_id = ? and catalog_item_id = ? order by department_id",
        UUID.class,
        organizationId,
        catalogItemId);
  }

  @Transactional(readOnly = true)
  public Map<UUID, List<UUID>> forItems(UUID organizationId, Collection<UUID> itemIds) {
    Map<UUID, List<UUID>> result = new HashMap<>();
    if (itemIds.isEmpty()) return result;
    String placeholders = String.join(",", itemIds.stream().map(ignored -> "?").toList());
    List<Object> args = new ArrayList<>();
    args.add(organizationId);
    args.addAll(itemIds);
    jdbc.query(
        "select catalog_item_id, department_id from catalog_item_department where organization_id = ? and catalog_item_id in ("
            + placeholders
            + ")",
        (rs, row) -> {
          result
              .computeIfAbsent(
                  rs.getObject("catalog_item_id", UUID.class), ignored -> new ArrayList<>())
              .add(rs.getObject("department_id", UUID.class));
          return null;
        },
        args.toArray());
    return result;
  }

  @Transactional(readOnly = true)
  public Set<UUID> itemIdsForDepartment(UUID organizationId, UUID departmentId) {
    return Set.copyOf(
        jdbc.queryForList(
            "select catalog_item_id from catalog_item_department where organization_id = ? and department_id = ?",
            UUID.class,
            organizationId,
            departmentId));
  }

  @Transactional(readOnly = true)
  public Set<UUID> favoriteItemIds(UUID organizationId, UUID userProfileId) {
    return Set.copyOf(
        jdbc.queryForList(
            "select catalog_item_id from catalog_favorite where organization_id = ? and user_profile_id = ?",
            UUID.class,
            organizationId,
            userProfileId));
  }

  @Transactional
  public void favorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    jdbc.update(
        """
        insert into catalog_favorite
            (organization_id, user_profile_id, catalog_item_id, created_at, created_by)
        values (?, ?, ?, now(), ?)
        on conflict do nothing
        """,
        organizationId,
        userProfileId,
        catalogItemId,
        actorProvider.currentActor());
  }

  @Transactional
  public void unfavorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    jdbc.update(
        "delete from catalog_favorite where organization_id = ? and user_profile_id = ? and catalog_item_id = ?",
        organizationId,
        userProfileId,
        catalogItemId);
  }

  @Transactional(readOnly = true)
  public Set<UUID> visibleForUser(UUID organizationId, UUID departmentId, UUID userProfileId) {
    Set<UUID> ids = new LinkedHashSet<>(itemIdsForDepartment(organizationId, departmentId));
    ids.addAll(favoriteItemIds(organizationId, userProfileId));
    return ids;
  }
}
