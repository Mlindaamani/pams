package com.company.procurement.organization.infrastructure;

import com.company.procurement.organization.domain.Department;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaDepartmentRepository
    extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {

  Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

  boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
      UUID organizationId, String name, UUID id);
}
