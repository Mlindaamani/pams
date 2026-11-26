package com.company.procurement.platform.repositories;

import java.util.List;
import java.util.UUID;

/** Destructive tenant data removal. */
public interface TenantDeletionRepository {

  List<String> userSubjects(UUID organizationId);

  void deleteTenant(UUID organizationId);
}
