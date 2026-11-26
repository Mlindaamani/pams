package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.RolePermission;
import com.company.procurement.access.domain.RolePermissionId;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Persistence port for role-permission assignments. */
public interface RolePermissionRepository {

  Set<String> findPermissionCodesByRoleName(String roleName);

  Set<String> findPermissionCodesByRoleNames(Collection<String> roleNames);

  List<RolePermission> findByRoleNames(Collection<String> roleNames);

  RolePermission save(RolePermission assignment);

  List<RolePermission> saveAll(List<RolePermission> assignments);

  void deleteById(RolePermissionId id);

  void deleteByRoleName(String roleName);
}
