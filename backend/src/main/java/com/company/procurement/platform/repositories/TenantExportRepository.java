package com.company.procurement.platform.repositories;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

/** Read-only tenant data export. */
public interface TenantExportRepository {

  List<String> tenantTables();

  List<String> storageKeys(UUID organizationId);

  void writeOrganization(UUID organizationId, OutputStream output);

  void writeTable(UUID organizationId, String table, OutputStream output);
}
