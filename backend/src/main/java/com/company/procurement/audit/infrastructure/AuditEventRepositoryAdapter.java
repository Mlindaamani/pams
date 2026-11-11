package com.company.procurement.audit.infrastructure;

import com.company.procurement.audit.domain.AuditEvent;
import com.company.procurement.audit.domain.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepositoryAdapter implements AuditEventRepository {

  private final JpaAuditEventRepository jpa;

  public AuditEventRepositoryAdapter(JpaAuditEventRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AuditEvent save(AuditEvent event) {
    return jpa.save(event);
  }

  @Override
  public Page<AuditEvent> search(
      UUID organizationId, String aggregateType, UUID aggregateId, Pageable pageable) {
    return jpa.findAll(specification(organizationId, aggregateType, aggregateId), pageable);
  }

  private static Specification<AuditEvent> specification(
      UUID organizationId, String aggregateType, UUID aggregateId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (aggregateType != null && !aggregateType.isBlank()) {
        predicates.add(cb.equal(root.get("aggregateType"), aggregateType));
      }
      if (aggregateId != null) {
        predicates.add(cb.equal(root.get("aggregateId"), aggregateId));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
