package com.company.procurement.catalog.application;

import com.company.procurement.catalog.domain.CatalogItem;
import com.company.procurement.organization.application.UserProfileLookupService;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides favorite and popular catalog discovery. */
@Service
public class CatalogDiscoveryService {

  private final JdbcTemplate jdbc;
  private final CatalogItemService items;
  private final CatalogAudienceService audiences;
  private final UserProfileLookupService profiles;
  private final CurrentUser currentUser;

  public CatalogDiscoveryService(
      JdbcTemplate jdbc,
      CatalogItemService items,
      CatalogAudienceService audiences,
      UserProfileLookupService profiles,
      CurrentUser currentUser) {
    this.jdbc = jdbc;
    this.items = items;
    this.audiences = audiences;
    this.profiles = profiles;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> favorites() {
    UserProfile profile = requireProfile();
    UUID organizationId = TenantContext.require();
    List<UUID> ids = audiences.favoriteItemIds(organizationId, profile.getId()).stream().toList();
    return ids.stream()
        .map(
            id -> {
              try {
                return items.get(id);
              } catch (RuntimeException ex) {
                return null;
              }
            })
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> popular(UUID departmentId, int limit) {
    UserProfile profile = requireProfile();
    UUID organizationId = TenantContext.require();
    UUID targetDepartment = departmentId == null ? profile.getDepartmentId() : departmentId;
    int boundedLimit = Math.min(Math.max(limit, 1), 20);
    List<UUID> ids =
        jdbc.queryForList(
            """
            select ci.id, count(*) uses
              from purchase_request_line l
              join purchase_request r on r.id = l.purchase_request_id
              join catalog_item ci on ci.id = l.catalog_item_id
             where l.organization_id = ?
               and r.department_id = ?
               and l.catalog_item_id is not null
               and r.status <> 'DRAFT'
               and ci.deleted_at is null
               and ci.active = true
               and (
                   ci.department_scope = 'ALL'
                   or exists (
                       select 1 from catalog_item_department d
                        where d.catalog_item_id = ci.id and d.department_id = ?
                   )
               )
             group by ci.id
             order by uses desc, max(r.created_at) desc
             limit ?
            """,
            UUID.class,
            organizationId,
            targetDepartment,
            targetDepartment,
            boundedLimit);
    return ids.stream().map(items::get).toList();
  }

  @Transactional
  public void favorite(UUID catalogItemId) {
    UserProfile profile = requireProfile();
    items.get(catalogItemId);
    audiences.favorite(TenantContext.require(), profile.getId(), catalogItemId);
  }

  @Transactional
  public void unfavorite(UUID catalogItemId) {
    UserProfile profile = requireProfile();
    audiences.unfavorite(TenantContext.require(), profile.getId(), catalogItemId);
  }

  private UserProfile requireProfile() {
    return profiles
        .findBySubject(currentUser.subject())
        .orElseThrow(
            () ->
                new BusinessRuleException("Create a user profile before using catalog favorites"));
  }
}
