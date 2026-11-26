package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.RoleDefinition;
import java.util.List;

/** Persistence port for tenant roles. */
public interface RoleRepository {

  List<RoleDefinition> findTenantRoles();

  boolean existsByRoleName(String roleName);
}
