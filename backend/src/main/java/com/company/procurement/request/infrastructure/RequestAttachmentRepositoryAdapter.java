package com.company.procurement.request.infrastructure;

import com.company.procurement.request.domain.RequestAttachment;
import com.company.procurement.request.domain.RequestAttachmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RequestAttachmentRepositoryAdapter implements RequestAttachmentRepository {

  private final JpaRequestAttachmentRepository jpa;

  public RequestAttachmentRepositoryAdapter(JpaRequestAttachmentRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public RequestAttachment save(RequestAttachment attachment) {
    return jpa.save(attachment);
  }

  @Override
  public Optional<RequestAttachment> findByIdAndOrganizationIdAndRequestId(
      UUID id, UUID organizationId, UUID requestId) {
    return jpa.findByIdAndOrganizationIdAndRequestId(id, organizationId, requestId);
  }

  @Override
  public List<RequestAttachment> findAllByOrganizationIdAndRequestId(
      UUID organizationId, UUID requestId) {
    return jpa.findAllByOrganizationIdAndRequestIdOrderByCreatedAtAsc(organizationId, requestId);
  }

  @Override
  public long countByOrganizationIdAndRequestId(UUID organizationId, UUID requestId) {
    return jpa.countByOrganizationIdAndRequestId(organizationId, requestId);
  }

  @Override
  public void delete(RequestAttachment attachment) {
    jpa.delete(attachment);
  }
}
