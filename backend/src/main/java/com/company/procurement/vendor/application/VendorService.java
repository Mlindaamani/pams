package com.company.procurement.vendor.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.outbox.OutboxService;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.vendor.domain.Vendor;
import com.company.procurement.vendor.domain.VendorRepository;
import com.company.procurement.vendor.domain.VendorStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages vendor records and lifecycle status. */
@Service
public class VendorService {

  private final VendorRepository vendors;
  private final AuditEventService audit;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final CurrentActorProvider actorProvider;
  private final OutboxService outbox;

  public VendorService(
      VendorRepository vendors,
      AuditEventService audit,
      JdbcTemplate jdbc,
      Clock clock,
      CurrentActorProvider actorProvider,
      OutboxService outbox) {
    this.vendors = vendors;
    this.audit = audit;
    this.jdbc = jdbc;
    this.clock = clock;
    this.actorProvider = actorProvider;
    this.outbox = outbox;
  }

  @Transactional
  public Vendor create(CreateVendorCommand command) {
    UUID organizationId = TenantContext.require();
    String taxId = command.taxId().trim();
    if (vendors.existsByOrganizationIdAndTaxId(organizationId, taxId)) {
      throw new ConflictException("A vendor with tax id '%s' already exists".formatted(taxId));
    }
    Vendor vendor = new Vendor(organizationId, command.name(), taxId);
    vendor.updateDetails(
        command.name(),
        taxId,
        command.contactName(),
        command.contactEmail(),
        command.contactPhone(),
        command.addressLine(),
        command.bankName(),
        command.bankAccountName(),
        command.bankAccountNumber());
    Vendor saved = vendors.save(vendor);
    audit.recordCurrentActor(
        "vendor",
        saved.getId(),
        "vendor_created",
        Map.of("name", saved.getName(), "taxId", saved.getTaxId()));
    return saved;
  }

  @Transactional
  public Vendor update(UUID id, UpdateVendorCommand command) {
    UUID organizationId = TenantContext.require();
    Vendor vendor = get(id);
    boolean bankDetailsChanged =
        !java.util.Objects.equals(vendor.getBankName(), command.bankName())
            || !java.util.Objects.equals(vendor.getBankAccountName(), command.bankAccountName())
            || !java.util.Objects.equals(
                vendor.getBankAccountNumber(), command.bankAccountNumber());
    String taxId = command.taxId().trim();
    if (vendors.existsByOrganizationIdAndTaxIdAndIdNot(organizationId, taxId, id)) {
      throw new ConflictException("A vendor with tax id '%s' already exists".formatted(taxId));
    }
    vendor.updateDetails(
        command.name(),
        taxId,
        command.contactName(),
        command.contactEmail(),
        command.contactPhone(),
        command.addressLine(),
        command.bankName(),
        command.bankAccountName(),
        command.bankAccountNumber());
    Vendor saved = vendors.save(vendor);
    audit.recordCurrentActor(
        "vendor",
        saved.getId(),
        "vendor_updated",
        Map.of("name", saved.getName(), "bankDetailsChanged", bankDetailsChanged));
    return saved;
  }

  @Transactional
  public Vendor activate(UUID id) {
    Vendor vendor = get(id);
    VendorStatus previous = vendor.getStatus();
    vendor.activate();
    Vendor saved = vendors.save(vendor);
    audit.recordCurrentActor("vendor", saved.getId(), "vendor_activated", Map.of());
    enqueueStatusChange(saved, previous);
    return saved;
  }

  @Transactional
  public Vendor suspend(UUID id) {
    Vendor vendor = get(id);
    VendorStatus previous = vendor.getStatus();
    vendor.suspend();
    Vendor saved = vendors.save(vendor);
    audit.recordCurrentActor("vendor", saved.getId(), "vendor_suspended", Map.of());
    enqueueStatusChange(saved, previous);
    return saved;
  }

  @Transactional
  public Vendor blacklist(UUID id) {
    Vendor vendor = get(id);
    VendorStatus previous = vendor.getStatus();
    vendor.blacklist();
    Vendor saved = vendors.save(vendor);
    audit.recordCurrentActor("vendor", saved.getId(), "vendor_blacklisted", Map.of());
    enqueueStatusChange(saved, previous);
    return saved;
  }

  private void enqueueStatusChange(Vendor vendor, VendorStatus previous) {
    outbox.enqueue(
        OutboxEventTypes.VENDOR_STATUS_CHANGED,
        "vendor",
        vendor.getId(),
        Map.of(
            "vendorId", vendor.getId().toString(),
            "name", vendor.getName(),
            "previousStatus", previous.name(),
            "status", vendor.getStatus().name()));
  }

  @Transactional
  public void delete(UUID id) {
    UUID organizationId = TenantContext.require();
    Vendor vendor = get(id);
    Long references =
        jdbc.queryForObject(
            """
            select (select count(*) from purchase_order where organization_id = ? and vendor_id = ?)
                 + (select count(*) from vendor_invoice where organization_id = ? and vendor_id = ?)
            """,
            Long.class,
            organizationId,
            id,
            organizationId,
            id);
    if (references != null && references > 0) {
      throw new BusinessRuleException(
          "This vendor is referenced by documents and cannot be deleted; suspend or blacklist it instead");
    }
    vendor.softDelete(clock.instant(), actorProvider.currentActor());
    Vendor saved = vendors.save(vendor);
    audit.recordCurrentActor("vendor", saved.getId(), "vendor_deleted", Map.of());
  }

  @Transactional
  public int bulkDelete(List<UUID> ids) {
    ids.forEach(this::delete);
    return ids.size();
  }

  @Transactional(readOnly = true)
  public Vendor get(UUID id) {
    UUID organizationId = TenantContext.require();
    return vendors
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Vendor %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public Page<Vendor> search(VendorStatus status, String text, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    return vendors.search(organizationId, status, text, pageable);
  }
}
