package com.company.procurement.catalog.infrastructure;

import com.company.procurement.catalog.domain.CatalogItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaCatalogItemRepository
    extends JpaRepository<CatalogItem, UUID>, JpaSpecificationExecutor<CatalogItem> {

  Optional<CatalogItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

  boolean existsByOrganizationIdAndSkuAndIdNot(UUID organizationId, String sku, UUID id);
}
