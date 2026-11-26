package com.company.procurement.access.repositories;

import com.company.procurement.shared.security.RolePermissionLookupPort;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class RolePermissionLookupAdapter implements RolePermissionLookupPort {

  private final RolePermissionRepository roles;

  public RolePermissionLookupAdapter(RolePermissionRepository roles) {
    this.roles = roles;
  }

  @Override
  public Set<String> permissionsForRoles(Collection<String> roleNames) {
    if (roleNames == null || roleNames.isEmpty()) {
      return Set.of();
    }
    return roles.findPermissionCodesByRoleNames(roleNames);
  }
}
