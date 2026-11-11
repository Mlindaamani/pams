package com.company.procurement.catalog.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CatalogItemRepository {

  CatalogItem save(CatalogItem item);

  void flush();

  Optional<CatalogItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

  boolean existsByOrganizationIdAndSkuAndIdNot(UUID organizationId, String sku, UUID id);

  Page<CatalogItem> search(
      UUID organizationId,
      String category,
      Boolean active,
      String text,
      UUID departmentId,
      Set<UUID> selectedDepartmentItemIds,
      Pageable pageable);
}
