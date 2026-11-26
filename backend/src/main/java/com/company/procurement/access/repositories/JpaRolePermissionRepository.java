package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.RolePermission;
import com.company.procurement.access.domain.RolePermissionId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRolePermissionRepository
    extends JpaRepository<RolePermission, RolePermissionId> {

  @Query(
      "select rp.id.permissionCode from RolePermission rp"
          + " where rp.id.roleName = :roleName order by rp.id.permissionCode")
  List<String> findPermissionCodesByRoleName(@Param("roleName") String roleName);

  @Query(
      "select rp.id.permissionCode from RolePermission rp"
          + " where rp.id.roleName in :roleNames order by rp.id.permissionCode")
  List<String> findPermissionCodesByRoleNames(@Param("roleNames") Collection<String> roleNames);

  List<RolePermission> findByIdRoleNameIn(Collection<String> roleNames);

  @Modifying
  @Query("delete from RolePermission rp where rp.id.roleName = :roleName")
  int deleteByRoleName(@Param("roleName") String roleName);
}
