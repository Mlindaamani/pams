package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.RoleDefinition;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryAdapter implements RoleRepository {

  private final JpaRoleRepository jpa;

  public RoleRepositoryAdapter(JpaRoleRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<RoleDefinition> findTenantRoles() {
    return jpa.findAll().stream()
        .filter(role -> !"PLATFORM_ADMIN".equals(role.getRoleName()))
        .sorted(java.util.Comparator.comparing(RoleDefinition::getRoleName))
        .toList();
  }

  @Override
  public boolean existsByRoleName(String roleName) {
    return jpa.existsById(roleName);
  }
}
