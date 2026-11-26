package com.company.procurement.platform.services;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.platform.repositories.TenantDeletionRepository;
import com.company.procurement.platform.repositories.TenantExportRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permanently removes a suspended tenant and schedules external cleanup. */
@Service
public class TenantDeletionService {

  private final PlatformTenantService tenants;
  private final TenantDeletionRepository deletion;
  private final TenantExportRepository export;
  private final PlatformAuditService audit;
  private final ApplicationEventPublisher events;

  public TenantDeletionService(
      PlatformTenantService tenants,
      TenantDeletionRepository deletion,
      TenantExportRepository export,
      PlatformAuditService audit,
      ApplicationEventPublisher events) {
    this.tenants = tenants;
    this.deletion = deletion;
    this.export = export;
    this.audit = audit;
    this.events = events;
  }

  @Transactional
  public void delete(UUID id, long expectedVersion, String confirmSlug) {
    Organization tenant = tenants.get(id);
    if (expectedVersion >= 0 && tenant.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The tenant was modified by another operation; reload and retry");
    }
    if (tenant.getStatus() != OrganizationStatus.SUSPENDED) {
      throw new BusinessRuleException("Suspend the tenant before deleting it");
    }
    if (confirmSlug == null || !tenant.getSlug().equals(confirmSlug.trim())) {
      throw new BusinessRuleException("Tenant slug confirmation does not match");
    }

    UUID previous = TenantContext.get().orElse(null);
    TenantContext.set(id);
    try {
      List<String> subjects = deletion.userSubjects(id);
      List<String> storageKeys = export.storageKeys(id);
      deletion.deleteTenant(id);
      audit.record(
          "tenant_deleted",
          id,
          Map.of(
              "slug", tenant.getSlug(),
              "users", subjects.size(),
              "assets", storageKeys.size()));
      events.publishEvent(new TenantDeletedEvent(id, subjects, storageKeys));
    } finally {
      if (previous == null) {
        TenantContext.clear();
      } else {
        TenantContext.set(previous);
      }
    }
  }
}
