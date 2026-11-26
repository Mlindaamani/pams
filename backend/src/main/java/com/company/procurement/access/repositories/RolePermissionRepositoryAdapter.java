package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.RolePermission;
import com.company.procurement.access.domain.RolePermissionId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class RolePermissionRepositoryAdapter implements RolePermissionRepository {

  private final JpaRolePermissionRepository jpa;

  public RolePermissionRepositoryAdapter(JpaRolePermissionRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Set<String> findPermissionCodesByRoleName(String roleName) {
    return new LinkedHashSet<>(jpa.findPermissionCodesByRoleName(roleName));
  }

  @Override
  public Set<String> findPermissionCodesByRoleNames(Collection<String> roleNames) {
    return new LinkedHashSet<>(jpa.findPermissionCodesByRoleNames(roleNames));
  }

  @Override
  public List<RolePermission> findByRoleNames(Collection<String> roleNames) {
    return jpa.findByIdRoleNameIn(roleNames);
  }

  @Override
  public RolePermission save(RolePermission assignment) {
    return jpa.save(assignment);
  }

  @Override
  public List<RolePermission> saveAll(List<RolePermission> assignments) {
    return jpa.saveAll(assignments);
  }

  @Override
  public void deleteById(RolePermissionId id) {
    jpa.deleteById(id);
  }

  @Override
  public void deleteByRoleName(String roleName) {
    jpa.deleteByRoleName(roleName);
  }
}
