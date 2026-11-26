package com.company.procurement.access.repositories;

import com.company.procurement.access.domain.RoleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRoleRepository extends JpaRepository<RoleDefinition, String> {}
