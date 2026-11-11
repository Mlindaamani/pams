package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.approval.domain.ApprovalPolicyRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalPolicyRepositoryAdapter implements ApprovalPolicyRepository {

  private final JpaApprovalPolicyRepository jpa;

  public ApprovalPolicyRepositoryAdapter(JpaApprovalPolicyRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public ApprovalPolicy save(ApprovalPolicy policy) {
    return jpa.save(policy);
  }

  @Override
  public Optional<ApprovalPolicy> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public Optional<ApprovalPolicy> findActiveByAmount(
      UUID organizationId, String entityType, BigDecimal amount) {
    return jpa.findActiveByAmount(organizationId, entityType, amount);
  }

  @Override
  public Page<ApprovalPolicy> search(UUID organizationId, Boolean active, Pageable pageable) {
    return jpa.findAll(specification(organizationId, active), pageable);
  }

  private static Specification<ApprovalPolicy> specification(UUID organizationId, Boolean active) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
