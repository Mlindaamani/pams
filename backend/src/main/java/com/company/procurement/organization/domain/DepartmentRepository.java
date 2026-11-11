package com.company.procurement.organization.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DepartmentRepository {

  Department save(Department department);

  Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

  boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
      UUID organizationId, String name, UUID id);

  Page<Department> search(UUID organizationId, String name, Pageable pageable);
}
