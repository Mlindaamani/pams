package com.company.procurement.platform.services;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.platform.repositories.TenantExportRepository;
import com.company.procurement.shared.storage.FileStoragePort;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Streams a tenant data archive for offboarding and compliance. */
@Service
public class TenantExportService {

  private static final Logger log = LoggerFactory.getLogger(TenantExportService.class);
  private static final int FORMAT_VERSION = 1;

  private final PlatformTenantService tenants;
  private final TenantExportRepository export;
  private final FileStoragePort storage;
  private final PlatformAuditService audit;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public TenantExportService(
      PlatformTenantService tenants,
      TenantExportRepository export,
      FileStoragePort storage,
      PlatformAuditService audit,
      ObjectMapper objectMapper,
      Clock clock) {
    this.tenants = tenants;
    this.export = export;
    this.storage = storage;
    this.audit = audit;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String filename(UUID tenantId) {
    Organization tenant = tenants.get(tenantId);
    return "tenant-%s-%d.zip".formatted(tenant.getSlug(), clock.instant().getEpochSecond());
  }

  public void writeArchive(UUID tenantId, OutputStream output) throws IOException {
    Organization tenant = tenants.get(tenantId);
    UUID previous = TenantContext.get().orElse(null);
    TenantContext.set(tenantId);
    try {
      List<String> tables = export.tenantTables();
      List<String> storageKeys = export.storageKeys(tenantId);
      try (ZipOutputStream zip = new ZipOutputStream(output)) {
        Map<String, Object> manifest =
            Map.of(
                "formatVersion",
                FORMAT_VERSION,
                "tenantId",
                tenantId,
                "tenantSlug",
                tenant.getSlug(),
                "exportedAt",
                clock.instant(),
                "tables",
                tables,
                "assetCount",
                storageKeys.size());
        writeEntry(zip, "manifest.json", objectMapper.writeValueAsBytes(manifest));
        ZipEntry tenantEntry = new ZipEntry("tenant.json");
        zip.putNextEntry(tenantEntry);
        export.writeOrganization(tenantId, zip);
        zip.closeEntry();
        for (String table : tables) {
          ZipEntry entry = new ZipEntry("data/" + table + ".json");
          zip.putNextEntry(entry);
          export.writeTable(tenantId, table, zip);
          zip.closeEntry();
        }
        for (String key : storageKeys) {
          writeAsset(zip, key);
        }
        zip.finish();
      }
      audit.record(
          "tenant_exported",
          tenantId,
          Map.of("tables", tables.size(), "assets", storageKeys.size()));
    } finally {
      if (previous == null) {
        TenantContext.clear();
      } else {
        TenantContext.set(previous);
      }
    }
  }

  private void writeAsset(ZipOutputStream zip, String storageKey) {
    String safeKey = storageKey.replace("..", "_").replaceAll("[^a-zA-Z0-9/._-]", "_");
    try (var input = storage.get(storageKey)) {
      zip.putNextEntry(new ZipEntry("files/" + safeKey));
      input.transferTo(zip);
      zip.closeEntry();
    } catch (RuntimeException | IOException ex) {
      log.warn("Could not include tenant asset {} in export", safeKey, ex);
    }
  }

  private static void writeEntry(ZipOutputStream zip, String name, byte[] content)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
    zip.closeEntry();
  }
}
