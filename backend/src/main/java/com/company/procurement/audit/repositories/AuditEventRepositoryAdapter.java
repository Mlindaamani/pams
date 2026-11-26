package com.company.procurement.audit.repositories;

import com.company.procurement.audit.domain.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  public List<AuditEvent> search(AuditEventQuery query, int limit) {
    return jpa.findAll(
            specification(query),
            PageRequest.of(0, limit, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))))
        .getContent();
  }

  @Override
  public List<AuditEvent> export(AuditEventQuery query) {
    return jpa.findAll(
        specification(query), Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
  }

  private static Specification<AuditEvent> specification(AuditEventQuery query) {
    return (root, criteriaQuery, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), query.organizationId()));
      if (query.aggregateType() != null && !query.aggregateType().isBlank()) {
        predicates.add(cb.equal(root.get("aggregateType"), query.aggregateType()));
      }
      if (query.aggregateId() != null) {
        predicates.add(cb.equal(root.get("aggregateId"), query.aggregateId()));
      }
      if (query.action() != null && !query.action().isBlank()) {
        predicates.add(cb.equal(root.get("action"), query.action()));
      }
      if (query.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
      }
      if (query.to() != null) {
        predicates.add(cb.lessThan(root.get("occurredAt"), query.to()));
      }
      if (query.beforeOccurredAt() != null && query.beforeId() != null) {
        predicates.add(
            cb.or(
                cb.lessThan(root.get("occurredAt"), query.beforeOccurredAt()),
                cb.and(
                    cb.equal(root.get("occurredAt"), query.beforeOccurredAt()),
                    cb.lessThan(root.get("id"), query.beforeId()))));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
