package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.DemoRequest;
import com.company.procurement.platform.domain.DemoRequestStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class DemoRequestRepositoryAdapter implements DemoRequestRepository {

  private final JpaDemoRequestRepository jpa;

  public DemoRequestRepositoryAdapter(JpaDemoRequestRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public DemoRequest save(DemoRequest request) {
    return jpa.save(request);
  }

  @Override
  public Page<DemoRequest> search(String query, DemoRequestStatus status, Pageable pageable) {
    return jpa.findAll(specification(query, status), pageable);
  }

  private static Specification<DemoRequest> specification(String query, DemoRequestStatus status) {
    return (root, criteriaQuery, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (query != null && !query.isBlank()) {
        String pattern = "%" + query.trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("company")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
