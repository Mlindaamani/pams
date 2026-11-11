package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.ApprovalInstance;
import com.company.procurement.approval.domain.ApprovalInstanceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalInstanceRepositoryAdapter implements ApprovalInstanceRepository {

  private final JpaApprovalInstanceRepository jpa;

  public ApprovalInstanceRepositoryAdapter(JpaApprovalInstanceRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public ApprovalInstance save(ApprovalInstance instance) {
    return jpa.save(instance);
  }

  @Override
  public Optional<ApprovalInstance> findByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId) {
    return jpa.findByOrganizationIdAndPurchaseRequestId(organizationId, purchaseRequestId);
  }

  @Override
  public Optional<ApprovalInstance> findDetailByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId) {
    return jpa.findDetail(organizationId, purchaseRequestId);
  }

  @Override
  public Optional<ApprovalInstance> findDetailByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findDetailById(id, organizationId);
  }

  @Override
  public void delete(ApprovalInstance instance) {
    jpa.delete(instance);
  }

  @Override
  public void deleteByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId) {
    jpa.deleteByRequest(organizationId, purchaseRequestId);
  }
}
