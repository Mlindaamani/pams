package com.company.procurement.approval.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApprovalPolicyRepository {

  ApprovalPolicy save(ApprovalPolicy policy);

  Optional<ApprovalPolicy> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<ApprovalPolicy> findActiveByAmount(
      UUID organizationId, String entityType, BigDecimal amount);

  Page<ApprovalPolicy> search(UUID organizationId, Boolean active, Pageable pageable);
}
