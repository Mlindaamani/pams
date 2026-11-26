package com.company.procurement.catalog.repositories;

import com.company.procurement.catalog.domain.CatalogItemDepartment;
import com.company.procurement.catalog.domain.CatalogItemDepartmentId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCatalogItemDepartmentRepository
    extends JpaRepository<CatalogItemDepartment, CatalogItemDepartmentId> {

  List<CatalogItemDepartment> findByIdCatalogItemIdOrderByIdDepartmentIdAsc(UUID catalogItemId);

  List<CatalogItemDepartment> findByIdCatalogItemIdIn(Collection<UUID> catalogItemIds);

  List<CatalogItemDepartment> findByOrganizationIdAndIdDepartmentId(
      UUID organizationId, UUID departmentId);

  @Modifying
  @Query(
      "delete from CatalogItemDepartment d"
          + " where d.organizationId = :organizationId and d.id.catalogItemId = :catalogItemId")
  int deleteByCatalogItem(
      @Param("organizationId") UUID organizationId, @Param("catalogItemId") UUID catalogItemId);
}
