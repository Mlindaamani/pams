package com.company.procurement.catalog.application;

import com.company.procurement.catalog.domain.CatalogDepartmentScope;
import com.company.procurement.catalog.domain.CatalogItem;
import com.company.procurement.catalog.domain.CatalogItemRepository;
import com.company.procurement.organization.application.DepartmentService;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages catalog item lifecycle and validation. */
@Service
public class CatalogItemService {

  private final CatalogItemRepository items;
  private final Clock clock;
  private final CurrentActorProvider actorProvider;
  private final DepartmentService departments;
  private final CatalogAudienceService audiences;

  public CatalogItemService(
      CatalogItemRepository items,
      Clock clock,
      CurrentActorProvider actorProvider,
      DepartmentService departments,
      CatalogAudienceService audiences) {
    this.items = items;
    this.clock = clock;
    this.actorProvider = actorProvider;
    this.departments = departments;
    this.audiences = audiences;
  }

  @Transactional
  public CatalogItem create(CreateCatalogItemCommand command) {
    UUID organizationId = TenantContext.require();
    String sku = command.sku().trim();
    if (items.existsByOrganizationIdAndSku(organizationId, sku)) {
      throw new ConflictException("A catalog item with SKU '%s' already exists".formatted(sku));
    }
    CatalogItem item =
        new CatalogItem(
            organizationId,
            sku,
            command.name(),
            command.unit(),
            command.defaultPrice(),
            command.currency(),
            command.category(),
            command.defaultVendorId(),
            normalizeScope(command.departmentScope()));
    validateAudience(organizationId, item.getDepartmentScope(), command.departmentIds());
    CatalogItem saved = items.save(item);
    items.flush();
    audiences.replace(
        organizationId, saved.getId(), saved.getDepartmentScope(), command.departmentIds());
    return enrich(saved);
  }

  @Transactional
  public CatalogItem update(UUID id, UpdateCatalogItemCommand command) {
    CatalogItem item = get(id);
    item.updateDetails(
        command.name(),
        command.unit(),
        command.defaultPrice(),
        command.currency(),
        command.category(),
        command.defaultVendorId(),
        normalizeScope(command.departmentScope()));
    item.setActive(command.active());
    UUID organizationId = item.getOrganizationId();
    validateAudience(organizationId, item.getDepartmentScope(), command.departmentIds());
    CatalogItem saved = items.save(item);
    items.flush();
    audiences.replace(
        organizationId, saved.getId(), saved.getDepartmentScope(), command.departmentIds());
    return enrich(saved);
  }

  @Transactional
  public void delete(UUID id) {
    CatalogItem item = get(id);
    item.softDelete(clock.instant(), actorProvider.currentActor());
    items.save(item);
  }

  @Transactional
  public int bulkDelete(List<UUID> ids) {
    ids.forEach(this::delete);
    return ids.size();
  }

  @Transactional(readOnly = true)
  public CatalogItem get(UUID id) {
    UUID organizationId = TenantContext.require();
    return enrich(
        items
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Catalog item %s not found".formatted(id))));
  }

  @Transactional(readOnly = true)
  public Page<CatalogItem> search(
      String category, Boolean active, String text, UUID departmentId, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    Set<UUID> selected =
        departmentId == null
            ? Set.of()
            : audiences.itemIdsForDepartment(organizationId, departmentId);
    Page<CatalogItem> page =
        items.search(organizationId, category, active, text, departmentId, selected, pageable);
    Map<UUID, List<UUID>> mappings =
        audiences.forItems(
            organizationId, page.getContent().stream().map(CatalogItem::getId).toList());
    page.getContent()
        .forEach(item -> item.setDepartmentIds(mappings.getOrDefault(item.getId(), List.of())));
    return page;
  }

  private CatalogItem enrich(CatalogItem item) {
    item.setDepartmentIds(audiences.forItem(item.getOrganizationId(), item.getId()));
    return item;
  }

  private static CatalogDepartmentScope normalizeScope(CatalogDepartmentScope scope) {
    return scope == null ? CatalogDepartmentScope.ALL : scope;
  }

  private void validateAudience(
      UUID organizationId, CatalogDepartmentScope scope, List<UUID> departmentIds) {
    if (scope != CatalogDepartmentScope.SELECTED) {
      return;
    }
    if (departmentIds == null || departmentIds.isEmpty()) {
      throw new com.company.procurement.shared.error.BusinessRuleException(
          "Select at least one department for a department-specific catalog item");
    }
    departmentIds.stream()
        .distinct()
        .forEach(
            departmentId -> {
              departments.get(departmentId);
            });
  }
}
