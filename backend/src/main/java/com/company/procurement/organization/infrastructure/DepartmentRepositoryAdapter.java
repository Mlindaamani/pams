package com.company.procurement.organization.infrastructure;

import com.company.procurement.organization.domain.Department;
import com.company.procurement.organization.domain.DepartmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepositoryAdapter implements DepartmentRepository {

  private final JpaDepartmentRepository jpa;

  public DepartmentRepositoryAdapter(JpaDepartmentRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Department save(Department department) {
    return jpa.save(department);
  }

  @Override
  public Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name) {
    return jpa.existsByOrganizationIdAndNameIgnoreCase(organizationId, name);
  }

  @Override
  public boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
      UUID organizationId, String name, UUID id) {
    return jpa.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, name, id);
  }

  @Override
  public Page<Department> search(UUID organizationId, String name, Pageable pageable) {
    return jpa.findAll(specification(organizationId, name), pageable);
  }

  private static Specification<Department> specification(UUID organizationId, String name) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }
}
