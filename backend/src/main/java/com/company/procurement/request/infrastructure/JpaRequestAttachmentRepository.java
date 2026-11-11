package com.company.procurement.request.infrastructure;

import com.company.procurement.request.domain.RequestAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRequestAttachmentRepository extends JpaRepository<RequestAttachment, UUID> {

  Optional<RequestAttachment> findByIdAndOrganizationIdAndRequestId(
      UUID id, UUID organizationId, UUID requestId);

  List<RequestAttachment> findAllByOrganizationIdAndRequestIdOrderByCreatedAtAsc(
      UUID organizationId, UUID requestId);

  long countByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);
}
