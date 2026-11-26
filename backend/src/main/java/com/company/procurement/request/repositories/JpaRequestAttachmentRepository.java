package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.RequestAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRequestAttachmentRepository extends JpaRepository<RequestAttachment, UUID> {

  Optional<RequestAttachment> findByIdAndOrganizationIdAndRequestId(
      UUID id, UUID organizationId, UUID requestId);

  List<RequestAttachment> findAllByOrganizationIdAndRequestIdOrderByCreatedAtAsc(
      UUID organizationId, UUID requestId);

  long countByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);

  @Query(
      "select coalesce(sum(a.sizeBytes), 0) from RequestAttachment a"
          + " where a.organizationId = :organizationId")
  long sumSizeBytes(@Param("organizationId") UUID organizationId);
}
