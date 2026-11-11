package com.company.procurement.request.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestAttachmentRepository {

  RequestAttachment save(RequestAttachment attachment);

  Optional<RequestAttachment> findByIdAndOrganizationIdAndRequestId(
      UUID id, UUID organizationId, UUID requestId);

  List<RequestAttachment> findAllByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);

  long countByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);

  void delete(RequestAttachment attachment);
}
