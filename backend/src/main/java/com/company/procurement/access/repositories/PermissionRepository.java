package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Persistence port for the permission catalogue. */
public interface PermissionRepository {

  List<Permission> findTenantPermissions();

  List<Permission> findAllByCodeIn(Collection<String> codes);

  Set<String> findAllCodes();

  boolean existsByCode(String code);
}
