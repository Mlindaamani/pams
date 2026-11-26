package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.Permission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaPermissionRepository extends JpaRepository<Permission, String> {

  List<Permission> findByCodeNotLikeOrderByCodeAsc(String prefix);

  List<Permission> findAllByCodeIn(Collection<String> codes);

  @Query("select p.code from Permission p")
  List<String> findAllCodes();
}
