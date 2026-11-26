package com.company.procurement.catalog.services;

import java.util.List;
import java.util.UUID;

/** Popular catalog items for a department, derived from request history. */
public interface CatalogPopularityPort {

  List<UUID> popularItemIds(UUID organizationId, UUID departmentId, int limit);
}
