package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.Permission;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class PermissionRepositoryAdapter implements PermissionRepository {

  private final JpaPermissionRepository jpa;

  public PermissionRepositoryAdapter(JpaPermissionRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Permission> findTenantPermissions() {
    return jpa.findByCodeNotLikeOrderByCodeAsc("platform:%");
  }

  @Override
  public List<Permission> findAllByCodeIn(Collection<String> codes) {
    return jpa.findAllByCodeIn(codes);
  }

  @Override
  public Set<String> findAllCodes() {
    return new LinkedHashSet<>(jpa.findAllCodes());
  }

  @Override
  public boolean existsByCode(String code) {
    return jpa.existsById(code);
  }
}
