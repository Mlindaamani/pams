package com.company.procurement.catalog.application;

import com.company.procurement.catalog.domain.CatalogItem;
import com.company.procurement.catalog.domain.CatalogItemRepository;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogItemService {

  private final CatalogItemRepository items;
  private final Clock clock;
  private final CurrentActorProvider actorProvider;

  public CatalogItemService(
      CatalogItemRepository items, Clock clock, CurrentActorProvider actorProvider) {
    this.items = items;
    this.clock = clock;
    this.actorProvider = actorProvider;
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
            command.category());
    return items.save(item);
  }

  @Transactional
  public CatalogItem update(UUID id, UpdateCatalogItemCommand command) {
    CatalogItem item = get(id);
    item.updateDetails(
        command.name(),
        command.unit(),
        command.defaultPrice(),
        command.currency(),
        command.category());
    item.setActive(command.active());
    return items.save(item);
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
    return items
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Catalog item %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public Page<CatalogItem> search(String category, Boolean active, String text, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    return items.search(organizationId, category, active, text, pageable);
  }
}
