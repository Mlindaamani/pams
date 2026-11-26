package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.CatalogReviewStatus;
import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPurchaseRequestRepository
    extends JpaRepository<PurchaseRequest, UUID>, JpaSpecificationExecutor<PurchaseRequest> {

  Optional<PurchaseRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  long countByOrganizationIdAndStatusNot(UUID organizationId, RequestStatus status);

  long countByOrganizationId(UUID organizationId);

  long countByOrganizationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      UUID organizationId, Instant from, Instant to);

  @Query(
      """
      select l.catalogItemId
        from PurchaseRequestLine l, PurchaseRequest r
       where l.organizationId = :organizationId
         and r.id = l.request.id
         and r.organizationId = :organizationId
         and r.departmentId = :departmentId
         and r.status <> com.company.procurement.request.domain.RequestStatus.DRAFT
         and l.catalogItemId is not null
         and exists (
             select 1 from com.company.procurement.catalog.domain.CatalogItem ci
              where ci.id = l.catalogItemId
                and ci.active = true
                and (ci.departmentScope = com.company.procurement.catalog.domain.CatalogDepartmentScope.ALL
                     or exists (
                         select 1 from CatalogItemDepartment d
                          where d.id.catalogItemId = ci.id and d.id.departmentId = :departmentId))
         )
       group by l.catalogItemId
       order by count(l) desc, max(r.createdAt) desc
      """)
  List<UUID> popularCatalogItemIds(
      @Param("organizationId") UUID organizationId,
      @Param("departmentId") UUID departmentId,
      Pageable pageable);

  @Query(
      """
      select l.id as id, r.id as requestId, r.requestNumber as requestNumber,
             r.requesterName as requesterName, d.name as departmentName,
             l.description as description, l.unit as unit, l.unitPrice as unitPrice,
             l.notes as notes, l.createdAt as createdAt
        from PurchaseRequestLine l
        join PurchaseRequest r on r.id = l.request.id
        join Department d on d.id = r.departmentId
       where l.organizationId = :organizationId
         and l.nonCatalog = true
         and l.catalogReviewStatus = com.company.procurement.request.domain.CatalogReviewStatus.PENDING
       order by l.createdAt desc
      """)
  List<NonCatalogReviewProjection> findPendingNonCatalog(
      @Param("organizationId") UUID organizationId);

  @Modifying
  @Query(
      """
      update PurchaseRequestLine l
         set l.catalogReviewStatus = :status, l.updatedAt = :now, l.updatedBy = :actor
       where l.organizationId = :organizationId
         and l.id = :lineId
         and l.nonCatalog = true
         and l.catalogReviewStatus = com.company.procurement.request.domain.CatalogReviewStatus.PENDING
      """)
  int resolveNonCatalogLine(
      @Param("organizationId") UUID organizationId,
      @Param("lineId") UUID lineId,
      @Param("status") CatalogReviewStatus status,
      @Param("actor") String actor,
      @Param("now") Instant now);

  interface NonCatalogReviewProjection {
    UUID getId();

    UUID getRequestId();

    String getRequestNumber();

    String getRequesterName();

    String getDepartmentName();

    String getDescription();

    String getUnit();

    java.math.BigDecimal getUnitPrice();

    String getNotes();

    Instant getCreatedAt();
  }

  @EntityGraph(attributePaths = "lines")
  @Query("select r from PurchaseRequest r where r.id = :id and r.organizationId = :organizationId")
  Optional<PurchaseRequest> findDetail(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
